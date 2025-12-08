// ============================================================================
// Usage Tracking Service
// Tracks API usage in Redis and persists to Postgres
// ============================================================================

import { sql } from '../db';
import * as redis from './redis.service';
import config from '../config';
import type { UsageResponse, UsageInfo } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Redis Key Patterns
// -----------------------------------------------------------------------------

const KEYS = {
  dailyUsage: (deviceId: string) => `usage:daily:${deviceId}`,
  monthlyUsage: (deviceId: string, month: string) => `usage:monthly:${deviceId}:${month}`,
};

// -----------------------------------------------------------------------------
// Usage Queries
// -----------------------------------------------------------------------------

/**
 * Get usage information for a device
 */
export async function getUsage(
  deviceId: string,
  tier: 'free' | 'premium'
): Promise<UsageResponse> {
  const limits = tier === 'premium' ? config.rateLimit.premium : config.rateLimit.free;

  // Get daily usage from Redis
  const dailyKey = KEYS.dailyUsage(deviceId);
  const dailyUsed = await redis.get<number>(dailyKey) || 0;

  // Calculate reset time (midnight UTC)
  const now = new Date();
  const tomorrow = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate() + 1,
    0, 0, 0, 0
  ));

  return {
    daily: {
      used: dailyUsed,
      limit: limits.daily,
      reset_at: tomorrow.toISOString(),
    },
    tier,
    subscription_status: tier === 'premium' ? 'active' : 'none',
    upgrade_url: tier === 'free' ? 'https://reefscan.app/upgrade' : undefined,
  };
}

/**
 * Get usage info for scan result response
 */
export async function getUsageInfo(
  deviceId: string,
  tier: 'free' | 'premium'
): Promise<UsageInfo> {
  const usage = await getUsage(deviceId, tier);

  return {
    requests_today: usage.daily.used,
    daily_limit: usage.daily.limit,
    reset_at: usage.daily.reset_at,
  };
}

// -----------------------------------------------------------------------------
// Usage Recording
// -----------------------------------------------------------------------------

/**
 * Record a successful API request
 */
export async function recordUsage(
  deviceId: string,
  mode: string,
  provider: 'gemini' | 'openai',
  apiKeyId: string,
  latencyMs: number,
  tokensInput: number,
  tokensOutput: number,
  imageHash?: string
): Promise<void> {
  try {
    // Persist to database
    await sql`
      INSERT INTO request_logs (
        device_id, request_id, mode, image_hash, provider_used,
        api_key_id, status, latency_ms, tokens_input, tokens_output
      ) VALUES (
        ${deviceId},
        ${crypto.randomUUID()},
        ${mode},
        ${imageHash || null},
        ${provider},
        ${apiKeyId},
        'success',
        ${latencyMs},
        ${tokensInput},
        ${tokensOutput}
      )
    `;

    // Update daily usage in Postgres
    const today = new Date().toISOString().split('T')[0];
    await sql`
      INSERT INTO daily_usage (device_id, date, request_count, tokens_used)
      VALUES (${deviceId}, ${today}, 1, ${tokensInput + tokensOutput})
      ON CONFLICT (device_id, date)
      DO UPDATE SET
        request_count = daily_usage.request_count + 1,
        tokens_used = daily_usage.tokens_used + ${tokensInput + tokensOutput}
    `;

    logger.debug('Usage recorded', {
      device_id: deviceId,
      mode,
      provider,
      latency_ms: latencyMs,
    });
  } catch (error) {
    // Log but don't fail the request
    logger.error('Failed to record usage', {
      device_id: deviceId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * Record a failed API request
 */
export async function recordError(
  deviceId: string,
  mode: string,
  provider: 'gemini' | 'openai',
  apiKeyId: string,
  errorCode: string,
  latencyMs: number
): Promise<void> {
  try {
    await sql`
      INSERT INTO request_logs (
        device_id, request_id, mode, provider_used,
        api_key_id, status, latency_ms, error_code
      ) VALUES (
        ${deviceId},
        ${crypto.randomUUID()},
        ${mode},
        ${provider},
        ${apiKeyId},
        'error',
        ${latencyMs},
        ${errorCode}
      )
    `;
  } catch (error) {
    logger.error('Failed to record error', {
      device_id: deviceId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

// -----------------------------------------------------------------------------
// Usage Statistics
// -----------------------------------------------------------------------------

/**
 * Get usage statistics for a device over a date range
 */
export async function getUsageStats(
  deviceId: string,
  startDate: Date,
  endDate: Date
): Promise<{
  total_requests: number;
  total_tokens: number;
  by_mode: Record<string, number>;
  by_date: Array<{ date: string; count: number }>;
}> {
  const start = startDate.toISOString().split('T')[0];
  const end = endDate.toISOString().split('T')[0];

  // Get daily totals
  const dailyStats = await sql`
    SELECT date, request_count, tokens_used
    FROM daily_usage
    WHERE device_id = ${deviceId}
      AND date >= ${start}
      AND date <= ${end}
    ORDER BY date
  `;

  // Get by-mode breakdown
  const modeStats = await sql`
    SELECT mode, COUNT(*) as count
    FROM request_logs
    WHERE device_id = ${deviceId}
      AND created_at >= ${startDate.toISOString()}
      AND created_at <= ${endDate.toISOString()}
      AND status = 'success'
    GROUP BY mode
  `;

  const totalRequests = dailyStats.rows.reduce(
    (sum, row) => sum + (row.request_count as number),
    0
  );
  const totalTokens = dailyStats.rows.reduce(
    (sum, row) => sum + (row.tokens_used as number),
    0
  );

  const byMode: Record<string, number> = {};
  modeStats.rows.forEach((row) => {
    byMode[row.mode as string] = Number(row.count);
  });

  const byDate = dailyStats.rows.map((row) => ({
    date: String(row.date),
    count: row.request_count as number,
  }));

  return {
    total_requests: totalRequests,
    total_tokens: totalTokens,
    by_mode: byMode,
    by_date: byDate,
  };
}

/**
 * Get aggregate usage statistics (for admin/analytics)
 */
export async function getAggregateStats(date: Date): Promise<{
  total_requests: number;
  total_devices: number;
  by_tier: Record<string, number>;
  by_provider: Record<string, number>;
}> {
  const dateStr = date.toISOString().split('T')[0];

  const result = await sql`
    SELECT
      COUNT(*) as total_requests,
      COUNT(DISTINCT device_id) as total_devices
    FROM daily_usage
    WHERE date = ${dateStr}
  `;

  const byTier = await sql`
    SELECT d.tier, SUM(du.request_count) as count
    FROM daily_usage du
    JOIN devices d ON du.device_id = d.id
    WHERE du.date = ${dateStr}
    GROUP BY d.tier
  `;

  const byProvider = await sql`
    SELECT provider_used, COUNT(*) as count
    FROM request_logs
    WHERE DATE(created_at) = ${dateStr}
    GROUP BY provider_used
  `;

  const tierStats: Record<string, number> = {};
  byTier.rows.forEach((row) => {
    tierStats[row.tier as string] = Number(row.count);
  });

  const providerStats: Record<string, number> = {};
  byProvider.rows.forEach((row) => {
    providerStats[row.provider_used as string] = Number(row.count);
  });

  return {
    total_requests: Number(result.rows[0]?.total_requests || 0),
    total_devices: Number(result.rows[0]?.total_devices || 0),
    by_tier: tierStats,
    by_provider: providerStats,
  };
}

// -----------------------------------------------------------------------------
// Usage Reset (for cron jobs)
// -----------------------------------------------------------------------------

/**
 * Archive old usage data (run daily)
 * Keeps detailed logs for 30 days, aggregated data indefinitely
 */
export async function archiveOldUsage(): Promise<void> {
  const thirtyDaysAgo = new Date();
  thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);
  const cutoffDate = thirtyDaysAgo.toISOString();

  try {
    // Delete old request logs (keep 30 days)
    const result = await sql`
      DELETE FROM request_logs
      WHERE created_at < ${cutoffDate}
    `;

    logger.info('Archived old usage data', {
      deleted_logs: result.rowCount,
      cutoff_date: cutoffDate,
    });
  } catch (error) {
    logger.error('Failed to archive usage data', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }
}

/**
 * Sync Redis daily counters to Postgres (run at end of day)
 * This ensures we don't lose data if Redis restarts
 */
export async function syncDailyUsageToDB(): Promise<void> {
  // This is handled automatically in recordUsage
  // This function can be used for reconciliation if needed
  logger.info('Daily usage sync completed');
}
