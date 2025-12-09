// ============================================================================
// Metrics Service
// Provides dashboard metrics from the database
// ============================================================================

import { sql } from '../db';

// API Pricing (per 1M tokens)
const PRICING = {
  gemini: {
    input: 0.075,   // $0.075 per 1M input tokens (Gemini 1.5 Flash)
    output: 0.30,   // $0.30 per 1M output tokens
  },
  openai: {
    input: 2.50,    // $2.50 per 1M input tokens (GPT-4o)
    output: 10.00,  // $10.00 per 1M output tokens
  },
};

export interface CostStats {
  totalCost: number;
  costToday: number;
  costLast7Days: number;
  costLast30Days: number;
  costByProvider: {
    gemini: number;
    openai: number;
  };
  tokensByProvider: {
    gemini: { input: number; output: number };
    openai: { input: number; output: number };
  };
  estimatedSavingsFromCache: number;
  avgCostPerRequest: number;
}

export interface DashboardMetrics {
  totalRequests: number;
  requestsToday: number;
  avgLatencyMs: number;
  errorRate: number;
  totalDevices: number;
  activeDevices: number;
  tierDistribution: {
    free: number;
    premium: number;
  };
  cacheStats: {
    totalEntries: number;
    hitCount: number;
  };
  costStats: CostStats;
  requestsByHour: Array<{
    hour: string;
    requests: number;
    errors: number;
  }>;
  endpointStats: Array<{
    mode: string;
    requests: number;
    avgLatency: number;
    errorRate: number;
  }>;
}

// Calculate cost from tokens
function calculateCost(
  provider: string,
  inputTokens: number,
  outputTokens: number
): number {
  const pricing = provider === 'openai' ? PRICING.openai : PRICING.gemini;
  const inputCost = (inputTokens / 1_000_000) * pricing.input;
  const outputCost = (outputTokens / 1_000_000) * pricing.output;
  return inputCost + outputCost;
}

export async function getDashboardMetrics(): Promise<DashboardMetrics> {
  try {
    // Run all queries in parallel for performance
    const [
    totalRequestsResult,
    requestsTodayResult,
    avgLatencyResult,
    errorRateResult,
    deviceStatsResult,
    tierDistResult,
    cacheStatsResult,
    hourlyStatsResult,
    endpointStatsResult,
    // Cost-related queries
    totalTokensResult,
    todayTokensResult,
    last7DaysTokensResult,
    last30DaysTokensResult,
    cacheHitsForSavingsResult,
  ] = await Promise.all([
    // Total requests all time
    sql<{ count: string }>`SELECT COUNT(*) as count FROM request_logs`,

    // Requests today
    sql<{ count: string }>`
      SELECT COUNT(*) as count FROM request_logs
      WHERE created_at >= CURRENT_DATE
    `,

    // Average latency (last 24h)
    sql<{ avg: string }>`
      SELECT COALESCE(AVG(latency_ms), 0) as avg FROM request_logs
      WHERE created_at >= NOW() - INTERVAL '24 hours'
        AND latency_ms IS NOT NULL
    `,

    // Error rate (last 24h)
    sql<{ total: string; errors: string }>`
      SELECT
        COUNT(*) as total,
        COUNT(*) FILTER (WHERE status = 'error') as errors
      FROM request_logs
      WHERE created_at >= NOW() - INTERVAL '24 hours'
    `,

    // Device stats
    sql<{ total: string; active: string }>`
      SELECT
        COUNT(*) as total,
        COUNT(*) FILTER (WHERE last_seen_at >= NOW() - INTERVAL '30 days') as active
      FROM devices
    `,

    // Tier distribution
    sql<{ tier: string; count: string }>`
      SELECT tier, COUNT(*) as count FROM devices GROUP BY tier
    `,

    // Cache stats
    sql<{ total: string; hits: string }>`
      SELECT
        COUNT(*) as total,
        COALESCE(SUM(hit_count), 0) as hits
      FROM image_cache
      WHERE expires_at > NOW() OR expires_at IS NULL
    `,

    // Hourly stats (last 24h)
    sql<{ hour: string; requests: string; errors: string }>`
      SELECT
        DATE_TRUNC('hour', created_at) as hour,
        COUNT(*) as requests,
        COUNT(*) FILTER (WHERE status = 'error') as errors
      FROM request_logs
      WHERE created_at >= NOW() - INTERVAL '24 hours'
      GROUP BY DATE_TRUNC('hour', created_at)
      ORDER BY hour
    `,

    // Endpoint/mode stats
    sql<{ mode: string; requests: string; avg_latency: string; errors: string }>`
      SELECT
        mode,
        COUNT(*) as requests,
        COALESCE(AVG(latency_ms), 0) as avg_latency,
        COUNT(*) FILTER (WHERE status = 'error') as errors
      FROM request_logs
      WHERE created_at >= NOW() - INTERVAL '24 hours'
      GROUP BY mode
      ORDER BY requests DESC
    `,

    // Total tokens by provider (all time)
    sql<{ provider: string; input_tokens: string; output_tokens: string }>`
      SELECT
        COALESCE(provider_used, 'gemini') as provider,
        COALESCE(SUM(tokens_input), 0) as input_tokens,
        COALESCE(SUM(tokens_output), 0) as output_tokens
      FROM request_logs
      WHERE tokens_input IS NOT NULL
      GROUP BY provider_used
    `,

    // Today's tokens by provider
    sql<{ provider: string; input_tokens: string; output_tokens: string }>`
      SELECT
        COALESCE(provider_used, 'gemini') as provider,
        COALESCE(SUM(tokens_input), 0) as input_tokens,
        COALESCE(SUM(tokens_output), 0) as output_tokens
      FROM request_logs
      WHERE created_at >= CURRENT_DATE AND tokens_input IS NOT NULL
      GROUP BY provider_used
    `,

    // Last 7 days tokens by provider
    sql<{ provider: string; input_tokens: string; output_tokens: string }>`
      SELECT
        COALESCE(provider_used, 'gemini') as provider,
        COALESCE(SUM(tokens_input), 0) as input_tokens,
        COALESCE(SUM(tokens_output), 0) as output_tokens
      FROM request_logs
      WHERE created_at >= NOW() - INTERVAL '7 days' AND tokens_input IS NOT NULL
      GROUP BY provider_used
    `,

    // Last 30 days tokens by provider
    sql<{ provider: string; input_tokens: string; output_tokens: string }>`
      SELECT
        COALESCE(provider_used, 'gemini') as provider,
        COALESCE(SUM(tokens_input), 0) as input_tokens,
        COALESCE(SUM(tokens_output), 0) as output_tokens
      FROM request_logs
      WHERE created_at >= NOW() - INTERVAL '30 days' AND tokens_input IS NOT NULL
      GROUP BY provider_used
    `,

    // Cache hits for savings calculation (average tokens per cached request)
    sql<{ hits: string; avg_input: string; avg_output: string }>`
      SELECT
        COALESCE(SUM(hit_count), 0) as hits,
        COALESCE((SELECT AVG(tokens_input) FROM request_logs WHERE tokens_input IS NOT NULL), 0) as avg_input,
        COALESCE((SELECT AVG(tokens_output) FROM request_logs WHERE tokens_output IS NOT NULL), 0) as avg_output
      FROM image_cache
      WHERE expires_at > NOW() OR expires_at IS NULL
    `,
  ]);

  // Parse tier distribution
  const tierDistribution = { free: 0, premium: 0 };
  for (const row of tierDistResult.rows) {
    if (row.tier === 'free') tierDistribution.free = parseInt(row.count, 10);
    if (row.tier === 'premium') tierDistribution.premium = parseInt(row.count, 10);
  }

  // Calculate error rate
  const totalLast24h = parseInt(errorRateResult.rows[0]?.total || '0', 10);
  const errorsLast24h = parseInt(errorRateResult.rows[0]?.errors || '0', 10);
  const errorRate = totalLast24h > 0 ? (errorsLast24h / totalLast24h) * 100 : 0;

  // Parse hourly stats
  const requestsByHour = hourlyStatsResult.rows.map((row) => ({
    hour: row.hour,
    requests: parseInt(row.requests, 10),
    errors: parseInt(row.errors, 10),
  }));

  // Parse endpoint stats
  const endpointStats = endpointStatsResult.rows.map((row) => {
    const requests = parseInt(row.requests, 10);
    const errors = parseInt(row.errors, 10);
    return {
      mode: row.mode,
      requests,
      avgLatency: Math.round(parseFloat(row.avg_latency)),
      errorRate: requests > 0 ? (errors / requests) * 100 : 0,
    };
  });

  // Helper to parse token results by provider
  const parseTokensByProvider = (result: { rows: Array<{ provider: string; input_tokens: string; output_tokens: string }> }) => {
    const tokens = { gemini: { input: 0, output: 0 }, openai: { input: 0, output: 0 } };
    let totalCost = 0;
    for (const row of result.rows) {
      const provider = row.provider === 'openai' ? 'openai' : 'gemini';
      const input = parseInt(row.input_tokens, 10) || 0;
      const output = parseInt(row.output_tokens, 10) || 0;
      tokens[provider].input += input;
      tokens[provider].output += output;
      totalCost += calculateCost(provider, input, output);
    }
    return { tokens, totalCost };
  };

  // Calculate costs for different time periods
  const totalTokens = parseTokensByProvider(totalTokensResult);
  const todayTokens = parseTokensByProvider(todayTokensResult);
  const last7DaysTokens = parseTokensByProvider(last7DaysTokensResult);
  const last30DaysTokens = parseTokensByProvider(last30DaysTokensResult);

  // Calculate cache savings (hits * avg cost per request)
  const cacheHits = parseInt(cacheHitsForSavingsResult.rows[0]?.hits || '0', 10);
  const avgInputTokens = parseFloat(cacheHitsForSavingsResult.rows[0]?.avg_input || '0');
  const avgOutputTokens = parseFloat(cacheHitsForSavingsResult.rows[0]?.avg_output || '0');
  const estimatedSavingsFromCache = cacheHits * calculateCost('gemini', avgInputTokens, avgOutputTokens);

  // Calculate average cost per request
  const totalRequests = parseInt(totalRequestsResult.rows[0]?.count || '0', 10);
  const avgCostPerRequest = totalRequests > 0 ? totalTokens.totalCost / totalRequests : 0;

  // Build cost stats
  const costStats: CostStats = {
    totalCost: Math.round(totalTokens.totalCost * 10000) / 10000,
    costToday: Math.round(todayTokens.totalCost * 10000) / 10000,
    costLast7Days: Math.round(last7DaysTokens.totalCost * 10000) / 10000,
    costLast30Days: Math.round(last30DaysTokens.totalCost * 10000) / 10000,
    costByProvider: {
      gemini: Math.round(calculateCost('gemini', totalTokens.tokens.gemini.input, totalTokens.tokens.gemini.output) * 10000) / 10000,
      openai: Math.round(calculateCost('openai', totalTokens.tokens.openai.input, totalTokens.tokens.openai.output) * 10000) / 10000,
    },
    tokensByProvider: totalTokens.tokens,
    estimatedSavingsFromCache: Math.round(estimatedSavingsFromCache * 10000) / 10000,
    avgCostPerRequest: Math.round(avgCostPerRequest * 1000000) / 1000000,
  };

  return {
    totalRequests,
    requestsToday: parseInt(requestsTodayResult.rows[0]?.count || '0', 10),
    avgLatencyMs: Math.round(parseFloat(avgLatencyResult.rows[0]?.avg || '0')),
    errorRate: Math.round(errorRate * 100) / 100,
    totalDevices: parseInt(deviceStatsResult.rows[0]?.total || '0', 10),
    activeDevices: parseInt(deviceStatsResult.rows[0]?.active || '0', 10),
    tierDistribution,
    cacheStats: {
      totalEntries: parseInt(cacheStatsResult.rows[0]?.total || '0', 10),
      hitCount: parseInt(cacheStatsResult.rows[0]?.hits || '0', 10),
    },
    costStats,
    requestsByHour,
    endpointStats,
  };
  } catch (error) {
    // If tables don't exist yet, return default empty metrics
    console.error('Metrics query failed (tables may not exist):', error);
    return {
      totalRequests: 0,
      requestsToday: 0,
      avgLatencyMs: 0,
      errorRate: 0,
      totalDevices: 0,
      activeDevices: 0,
      tierDistribution: { free: 0, premium: 0 },
      cacheStats: { totalEntries: 0, hitCount: 0 },
      costStats: {
        totalCost: 0,
        costToday: 0,
        costLast7Days: 0,
        costLast30Days: 0,
        costByProvider: { gemini: 0, openai: 0 },
        tokensByProvider: {
          gemini: { input: 0, output: 0 },
          openai: { input: 0, output: 0 },
        },
        estimatedSavingsFromCache: 0,
        avgCostPerRequest: 0,
      },
      requestsByHour: [],
      endpointStats: [],
    };
  }
}
