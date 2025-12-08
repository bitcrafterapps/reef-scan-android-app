// ============================================================================
// Rate Limiting Service
// Sliding window rate limiter using Redis
// ============================================================================

import * as redis from './redis.service';
import config from '../config';
import type { RateLimitResult } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Redis Key Patterns
// -----------------------------------------------------------------------------

const KEYS = {
  // Daily usage counter per device
  dailyUsage: (deviceId: string) => `ratelimit:daily:${deviceId}`,
  // Per-minute counter per device
  minuteUsage: (deviceId: string, minute: number) => `ratelimit:minute:${deviceId}:${minute}`,
  // Global requests per minute
  globalRpm: (minute: number) => `ratelimit:global:${minute}`,
  // IP-based rate limit
  ipLimit: (ip: string, hour: number) => `ratelimit:ip:${ip}:${hour}`,
};

// -----------------------------------------------------------------------------
// Rate Limit Check
// -----------------------------------------------------------------------------

/**
 * Check if a request is allowed based on rate limits
 * Implements multiple rate limit checks:
 * 1. Daily limit per device (tier-based)
 * 2. Per-minute limit per device
 * 3. Global RPM limit
 */
export async function checkRateLimit(
  deviceId: string,
  tier: 'free' | 'premium'
): Promise<RateLimitResult> {
  const limits = tier === 'premium' ? config.rateLimit.premium : config.rateLimit.free;
  const now = new Date();
  const currentMinute = Math.floor(now.getTime() / 60000);

  // Check daily limit first (most likely to be hit)
  const dailyResult = await checkDailyLimit(deviceId, limits.daily, tier);
  if (!dailyResult.allowed) {
    return dailyResult;
  }

  // Check per-minute limit
  const minuteResult = await checkMinuteLimit(deviceId, limits.perMinute);
  if (!minuteResult.allowed) {
    return {
      ...minuteResult,
      tier,
      limit: limits.daily,
      remaining: dailyResult.remaining,
    };
  }

  // Check global RPM limit
  const globalResult = await checkGlobalLimit(currentMinute);
  if (!globalResult.allowed) {
    return {
      ...globalResult,
      tier,
      limit: limits.daily,
      remaining: dailyResult.remaining,
    };
  }

  // All checks passed - increment counters
  await incrementCounters(deviceId, currentMinute);

  // Return success with updated remaining count
  return {
    allowed: true,
    limit: limits.daily,
    remaining: dailyResult.remaining - 1,
    reset_at: getDailyResetTimestamp(),
    tier,
  };
}

/**
 * Check daily limit for a device
 */
async function checkDailyLimit(
  deviceId: string,
  limit: number,
  tier: 'free' | 'premium'
): Promise<RateLimitResult> {
  const key = KEYS.dailyUsage(deviceId);
  const currentUsage = await redis.get<number>(key) || 0;
  const remaining = Math.max(0, limit - currentUsage);
  const resetAt = getDailyResetTimestamp();

  if (currentUsage >= limit) {
    logger.rateLimit(deviceId, tier, limit, 0, true);

    return {
      allowed: false,
      limit,
      remaining: 0,
      reset_at: resetAt,
      tier,
      upgrade_url: tier === 'free' ? 'https://reefscan.app/upgrade' : undefined,
    };
  }

  return {
    allowed: true,
    limit,
    remaining,
    reset_at: resetAt,
    tier,
  };
}

/**
 * Check per-minute limit for a device
 */
async function checkMinuteLimit(
  deviceId: string,
  limit: number
): Promise<Omit<RateLimitResult, 'tier' | 'limit' | 'remaining'>> {
  const currentMinute = Math.floor(Date.now() / 60000);
  const key = KEYS.minuteUsage(deviceId, currentMinute);
  const currentUsage = await redis.get<number>(key) || 0;

  if (currentUsage >= limit) {
    // Calculate when the minute resets
    const resetAt = (currentMinute + 1) * 60;

    return {
      allowed: false,
      reset_at: resetAt,
    };
  }

  return {
    allowed: true,
    reset_at: (currentMinute + 1) * 60,
  };
}

/**
 * Check global RPM limit
 */
async function checkGlobalLimit(
  currentMinute: number
): Promise<Omit<RateLimitResult, 'tier' | 'limit' | 'remaining'>> {
  const key = KEYS.globalRpm(currentMinute);
  const currentUsage = await redis.get<number>(key) || 0;

  if (currentUsage >= config.rateLimit.global.rpm) {
    logger.warn('Global rate limit reached', {
      current_rpm: currentUsage,
      limit: config.rateLimit.global.rpm,
    });

    return {
      allowed: false,
      reset_at: (currentMinute + 1) * 60,
    };
  }

  return {
    allowed: true,
    reset_at: (currentMinute + 1) * 60,
  };
}

/**
 * Increment all rate limit counters after successful check
 */
async function incrementCounters(deviceId: string, currentMinute: number): Promise<void> {
  const dailyKey = KEYS.dailyUsage(deviceId);
  const minuteKey = KEYS.minuteUsage(deviceId, currentMinute);
  const globalKey = KEYS.globalRpm(currentMinute);

  // Increment daily counter
  const dailyCount = await redis.incr(dailyKey);
  if (dailyCount === 1) {
    // Set expiry to end of day (UTC)
    const secondsUntilMidnight = getSecondsUntilMidnightUTC();
    await redis.expire(dailyKey, secondsUntilMidnight);
  }

  // Increment minute counter (expires in 2 minutes)
  await redis.incr(minuteKey);
  await redis.expire(minuteKey, 120);

  // Increment global counter (expires in 2 minutes)
  await redis.incr(globalKey);
  await redis.expire(globalKey, 120);
}

// -----------------------------------------------------------------------------
// IP-Based Rate Limiting
// -----------------------------------------------------------------------------

/**
 * Check IP-based rate limit (for abuse prevention)
 */
export async function checkIpRateLimit(ip: string): Promise<boolean> {
  const currentHour = Math.floor(Date.now() / 3600000);
  const key = KEYS.ipLimit(ip, currentHour);
  const currentUsage = await redis.get<number>(key) || 0;

  if (currentUsage >= config.rateLimit.ipLimit.perHour) {
    logger.warn('IP rate limit exceeded', { ip, current: currentUsage });
    return false;
  }

  // Increment counter
  const count = await redis.incr(key);
  if (count === 1) {
    await redis.expire(key, 3600); // 1 hour
  }

  return true;
}

// -----------------------------------------------------------------------------
// Usage Queries
// -----------------------------------------------------------------------------

/**
 * Get current daily usage for a device
 */
export async function getDailyUsage(deviceId: string): Promise<number> {
  const key = KEYS.dailyUsage(deviceId);
  return await redis.get<number>(key) || 0;
}

/**
 * Get rate limit info without incrementing
 */
export async function getRateLimitInfo(
  deviceId: string,
  tier: 'free' | 'premium'
): Promise<RateLimitResult> {
  const limits = tier === 'premium' ? config.rateLimit.premium : config.rateLimit.free;
  const currentUsage = await getDailyUsage(deviceId);
  const remaining = Math.max(0, limits.daily - currentUsage);

  return {
    allowed: remaining > 0,
    limit: limits.daily,
    remaining,
    reset_at: getDailyResetTimestamp(),
    tier,
    upgrade_url: tier === 'free' && remaining === 0 ? 'https://reefscan.app/upgrade' : undefined,
  };
}

/**
 * Reset daily usage for a device (for testing/admin)
 */
export async function resetDailyUsage(deviceId: string): Promise<boolean> {
  const key = KEYS.dailyUsage(deviceId);
  return await redis.del(key);
}

// -----------------------------------------------------------------------------
// Helper Functions
// -----------------------------------------------------------------------------

/**
 * Get Unix timestamp for midnight UTC (when daily limits reset)
 */
function getDailyResetTimestamp(): number {
  const now = new Date();
  const tomorrow = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate() + 1,
    0, 0, 0, 0
  ));
  return Math.floor(tomorrow.getTime() / 1000);
}

/**
 * Get seconds until midnight UTC
 */
function getSecondsUntilMidnightUTC(): number {
  const now = new Date();
  const midnight = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate() + 1,
    0, 0, 0, 0
  ));
  return Math.floor((midnight.getTime() - now.getTime()) / 1000);
}

/**
 * Format reset time as ISO string
 */
export function formatResetTime(resetTimestamp: number): string {
  return new Date(resetTimestamp * 1000).toISOString();
}
