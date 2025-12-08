// ============================================================================
// API Key Pool Service
// Manages multiple Gemini API keys with load balancing and health monitoring
// ============================================================================

import * as redis from './redis.service';
import config from '../config';
import type { ApiKeyConfig, ApiKeyState } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Redis Key Patterns
// -----------------------------------------------------------------------------

const KEYS = {
  keyState: (keyId: string) => `keypool:state:${keyId}`,
  keyRpm: (keyId: string, minute: number) => `keypool:rpm:${keyId}:${minute}`,
  keyDaily: (keyId: string) => `keypool:daily:${keyId}`,
  keyErrors: (keyId: string) => `keypool:errors:${keyId}`,
  keySuccess: (keyId: string) => `keypool:success:${keyId}`,
};

// -----------------------------------------------------------------------------
// Key Pool Configuration
// -----------------------------------------------------------------------------

// Default RPM limits by tier (Gemini API limits)
const DEFAULT_RPM_LIMITS: Record<string, number> = {
  paid_tier_1: 60,   // 60 RPM for paid tier 1
  paid_tier_2: 1000, // 1000 RPM for paid tier 2
};

// Cooldown duration in seconds after 429
const COOLDOWN_DURATION_SECONDS = 60;

// Error rate threshold for auto-disable (5%)
const ERROR_RATE_THRESHOLD = 0.05;

// Minimum requests before error rate applies
const MIN_REQUESTS_FOR_ERROR_RATE = 10;

// -----------------------------------------------------------------------------
// Key Pool State
// -----------------------------------------------------------------------------

interface KeyPoolState {
  keys: Map<string, ApiKeyConfig>;
  initialized: boolean;
}

const state: KeyPoolState = {
  keys: new Map(),
  initialized: false,
};

// -----------------------------------------------------------------------------
// Initialization
// -----------------------------------------------------------------------------

/**
 * Initialize the key pool from environment variables
 */
export function initializeKeyPool(): void {
  if (state.initialized) {
    return;
  }

  const geminiKeys = config.gemini.keys;

  if (geminiKeys.length === 0) {
    logger.warn('No Gemini API keys configured');
    state.initialized = true;
    return;
  }

  geminiKeys.forEach((key, index) => {
    const keyId = `gemini_${index + 1}`;
    const keyConfig: ApiKeyConfig = {
      key,
      id: keyId,
      tier: 'paid_tier_1', // Default tier, can be configured per key
      rpmLimit: DEFAULT_RPM_LIMITS['paid_tier_1'],
      dailyQuota: null, // No daily quota by default
    };
    state.keys.set(keyId, keyConfig);
  });

  logger.info('Key pool initialized', {
    key_count: state.keys.size,
  });

  state.initialized = true;
}

/**
 * Get all configured keys
 */
export function getKeys(): ApiKeyConfig[] {
  if (!state.initialized) {
    initializeKeyPool();
  }
  return Array.from(state.keys.values());
}

// -----------------------------------------------------------------------------
// Key Selection
// -----------------------------------------------------------------------------

/**
 * Select the best available API key for a request
 * Returns null if no keys are available
 */
export async function selectKey(): Promise<ApiKeyState | null> {
  if (!state.initialized) {
    initializeKeyPool();
  }

  if (state.keys.size === 0) {
    return null;
  }

  const currentMinute = Math.floor(Date.now() / 60000);
  const availableKeys: ApiKeyState[] = [];

  // Get state for all keys
  for (const [keyId, keyConfig] of state.keys) {
    const keyState = await getKeyState(keyId, keyConfig, currentMinute);

    // Skip keys in cooldown
    if (keyState.inCooldown && keyState.cooldownUntil) {
      if (new Date() < keyState.cooldownUntil) {
        continue;
      }
      // Cooldown expired, clear it
      await clearCooldown(keyId);
      keyState.inCooldown = false;
      keyState.cooldownUntil = null;
    }

    // Skip keys at RPM limit
    if (keyState.currentRpm >= keyState.rpmLimit) {
      continue;
    }

    // Skip keys with high error rate
    if (keyState.errorRate > ERROR_RATE_THRESHOLD) {
      const totalRequests = await getTotalRequests(keyId);
      if (totalRequests >= MIN_REQUESTS_FOR_ERROR_RATE) {
        logger.warn('Key disabled due to high error rate', {
          key_id: keyId,
          error_rate: keyState.errorRate,
        });
        continue;
      }
    }

    availableKeys.push(keyState);
  }

  if (availableKeys.length === 0) {
    logger.warn('No API keys available', {
      total_keys: state.keys.size,
    });
    return null;
  }

  // Sort by current RPM (least loaded first)
  availableKeys.sort((a, b) => a.currentRpm - b.currentRpm);

  // Select the least loaded key
  const selectedKey = availableKeys[0];

  // Log key selection
  logger.debug('API key selected', {
    key_id: selectedKey.id,
    current_rpm: selectedKey.currentRpm,
    available_keys: availableKeys.length,
  });

  return selectedKey;
}

/**
 * Get the current state of a key
 */
async function getKeyState(
  keyId: string,
  keyConfig: ApiKeyConfig,
  currentMinute: number
): Promise<ApiKeyState> {
  // Get RPM count
  const rpmKey = KEYS.keyRpm(keyId, currentMinute);
  const currentRpm = await redis.get<number>(rpmKey) || 0;

  // Get daily count
  const dailyKey = KEYS.keyDaily(keyId);
  const requestsToday = await redis.get<number>(dailyKey) || 0;

  // Get cooldown state
  const stateKey = KEYS.keyState(keyId);
  const savedState = await redis.get<{
    inCooldown: boolean;
    cooldownUntil: string | null;
  }>(stateKey);

  // Calculate error rate
  const errorRate = await getErrorRate(keyId);

  return {
    ...keyConfig,
    inCooldown: savedState?.inCooldown || false,
    cooldownUntil: savedState?.cooldownUntil ? new Date(savedState.cooldownUntil) : null,
    requestsToday,
    currentRpm,
    errorRate,
  };
}

// -----------------------------------------------------------------------------
// Key Usage Tracking
// -----------------------------------------------------------------------------

/**
 * Record a successful request for a key
 */
export async function recordSuccess(keyId: string): Promise<void> {
  const currentMinute = Math.floor(Date.now() / 60000);

  // Increment RPM counter
  const rpmKey = KEYS.keyRpm(keyId, currentMinute);
  await redis.incr(rpmKey);
  await redis.expire(rpmKey, 120); // Expire after 2 minutes

  // Increment daily counter
  const dailyKey = KEYS.keyDaily(keyId);
  const count = await redis.incr(dailyKey);
  if (count === 1) {
    // Set expiry to end of day
    const secondsUntilMidnight = getSecondsUntilMidnightUTC();
    await redis.expire(dailyKey, secondsUntilMidnight);
  }

  // Increment success counter
  const successKey = KEYS.keySuccess(keyId);
  await redis.incr(successKey);
  await redis.expire(successKey, 3600); // 1 hour window
}

/**
 * Record a failed request for a key
 */
export async function recordFailure(keyId: string, statusCode?: number): Promise<void> {
  // Increment error counter
  const errorKey = KEYS.keyErrors(keyId);
  await redis.incr(errorKey);
  await redis.expire(errorKey, 3600); // 1 hour window

  // Handle 429 (rate limited) - put key in cooldown
  if (statusCode === 429) {
    await setCooldown(keyId);
  }

  logger.warn('API key request failed', {
    key_id: keyId,
    status_code: statusCode,
  });
}

/**
 * Set a key into cooldown mode
 */
async function setCooldown(keyId: string): Promise<void> {
  const cooldownUntil = new Date(Date.now() + COOLDOWN_DURATION_SECONDS * 1000);

  const stateKey = KEYS.keyState(keyId);
  await redis.set(stateKey, {
    inCooldown: true,
    cooldownUntil: cooldownUntil.toISOString(),
  }, COOLDOWN_DURATION_SECONDS + 10);

  logger.warn('API key put in cooldown', {
    key_id: keyId,
    cooldown_until: cooldownUntil.toISOString(),
  });
}

/**
 * Clear cooldown for a key
 */
async function clearCooldown(keyId: string): Promise<void> {
  const stateKey = KEYS.keyState(keyId);
  await redis.del(stateKey);

  logger.info('API key cooldown cleared', {
    key_id: keyId,
  });
}

/**
 * Get error rate for a key (errors / total requests)
 */
async function getErrorRate(keyId: string): Promise<number> {
  const errorKey = KEYS.keyErrors(keyId);
  const successKey = KEYS.keySuccess(keyId);

  const [errors, successes] = await Promise.all([
    redis.get<number>(errorKey) || 0,
    redis.get<number>(successKey) || 0,
  ]);

  const total = (errors || 0) + (successes || 0);
  if (total === 0) {
    return 0;
  }

  return (errors || 0) / total;
}

/**
 * Get total requests for a key (for error rate calculation)
 */
async function getTotalRequests(keyId: string): Promise<number> {
  const errorKey = KEYS.keyErrors(keyId);
  const successKey = KEYS.keySuccess(keyId);

  const [errors, successes] = await Promise.all([
    redis.get<number>(errorKey) || 0,
    redis.get<number>(successKey) || 0,
  ]);

  return (errors || 0) + (successes || 0);
}

// -----------------------------------------------------------------------------
// Key Pool Metrics
// -----------------------------------------------------------------------------

/**
 * Get metrics for all keys in the pool
 */
export async function getKeyPoolMetrics(): Promise<{
  total_keys: number;
  available_keys: number;
  keys_in_cooldown: number;
  total_rpm: number;
  keys: Array<{
    id: string;
    current_rpm: number;
    requests_today: number;
    error_rate: number;
    in_cooldown: boolean;
  }>;
}> {
  if (!state.initialized) {
    initializeKeyPool();
  }

  const currentMinute = Math.floor(Date.now() / 60000);
  const keyMetrics: Array<{
    id: string;
    current_rpm: number;
    requests_today: number;
    error_rate: number;
    in_cooldown: boolean;
  }> = [];

  let availableKeys = 0;
  let keysInCooldown = 0;
  let totalRpm = 0;

  for (const [keyId, keyConfig] of state.keys) {
    const keyState = await getKeyState(keyId, keyConfig, currentMinute);

    keyMetrics.push({
      id: keyId,
      current_rpm: keyState.currentRpm,
      requests_today: keyState.requestsToday,
      error_rate: keyState.errorRate,
      in_cooldown: keyState.inCooldown,
    });

    totalRpm += keyState.currentRpm;

    if (keyState.inCooldown) {
      keysInCooldown++;
    } else if (keyState.currentRpm < keyState.rpmLimit) {
      availableKeys++;
    }
  }

  // Alert if fewer than 2 keys available
  if (availableKeys < 2 && state.keys.size >= 2) {
    logger.warn('Low API key availability', {
      available_keys: availableKeys,
      total_keys: state.keys.size,
    });
  }

  return {
    total_keys: state.keys.size,
    available_keys: availableKeys,
    keys_in_cooldown: keysInCooldown,
    total_rpm: totalRpm,
    keys: keyMetrics,
  };
}

/**
 * Check if the key pool has available capacity
 */
export async function hasAvailableCapacity(): Promise<boolean> {
  const key = await selectKey();
  return key !== null;
}

// -----------------------------------------------------------------------------
// Helper Functions
// -----------------------------------------------------------------------------

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

// Initialize on module load
initializeKeyPool();
