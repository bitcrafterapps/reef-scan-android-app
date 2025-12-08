// ============================================================================
// Redis Service (Upstash)
// Used for rate limiting, caching, and session management
// ============================================================================

import { Redis } from '@upstash/redis';

// Initialize Redis client
const redis = new Redis({
  url: process.env.UPSTASH_REDIS_REST_URL || '',
  token: process.env.UPSTASH_REDIS_REST_TOKEN || '',
});

/**
 * Test Redis connection
 */
export async function testConnection(): Promise<boolean> {
  try {
    await redis.ping();
    return true;
  } catch (error) {
    console.error('Redis connection failed:', error);
    return false;
  }
}

/**
 * Get a value from Redis
 */
export async function get<T>(key: string): Promise<T | null> {
  try {
    const value = await redis.get<T>(key);
    return value;
  } catch (error) {
    console.error(`Redis GET failed for key ${key}:`, error);
    return null;
  }
}

/**
 * Set a value in Redis with optional TTL
 */
export async function set(
  key: string,
  value: unknown,
  ttlSeconds?: number
): Promise<boolean> {
  try {
    if (ttlSeconds) {
      await redis.set(key, value, { ex: ttlSeconds });
    } else {
      await redis.set(key, value);
    }
    return true;
  } catch (error) {
    console.error(`Redis SET failed for key ${key}:`, error);
    return false;
  }
}

/**
 * Increment a counter
 */
export async function incr(key: string): Promise<number> {
  try {
    return await redis.incr(key);
  } catch (error) {
    console.error(`Redis INCR failed for key ${key}:`, error);
    return 0;
  }
}

/**
 * Set expiration on a key
 */
export async function expire(key: string, seconds: number): Promise<boolean> {
  try {
    await redis.expire(key, seconds);
    return true;
  } catch (error) {
    console.error(`Redis EXPIRE failed for key ${key}:`, error);
    return false;
  }
}

/**
 * Get TTL of a key
 */
export async function ttl(key: string): Promise<number> {
  try {
    return await redis.ttl(key);
  } catch (error) {
    console.error(`Redis TTL failed for key ${key}:`, error);
    return -1;
  }
}

/**
 * Delete a key
 */
export async function del(key: string): Promise<boolean> {
  try {
    await redis.del(key);
    return true;
  } catch (error) {
    console.error(`Redis DEL failed for key ${key}:`, error);
    return false;
  }
}

/**
 * Check if key exists
 */
export async function exists(key: string): Promise<boolean> {
  try {
    const result = await redis.exists(key);
    return result === 1;
  } catch (error) {
    console.error(`Redis EXISTS failed for key ${key}:`, error);
    return false;
  }
}

/**
 * Get multiple keys
 */
export async function mget<T>(keys: string[]): Promise<(T | null)[]> {
  try {
    return await redis.mget<T[]>(...keys);
  } catch (error) {
    console.error(`Redis MGET failed:`, error);
    return keys.map(() => null);
  }
}

/**
 * Hash operations
 */
export const hash = {
  async get<T>(key: string, field: string): Promise<T | null> {
    try {
      return await redis.hget<T>(key, field);
    } catch (error) {
      console.error(`Redis HGET failed for ${key}:${field}:`, error);
      return null;
    }
  },

  async set(key: string, field: string, value: unknown): Promise<boolean> {
    try {
      await redis.hset(key, { [field]: value });
      return true;
    } catch (error) {
      console.error(`Redis HSET failed for ${key}:${field}:`, error);
      return false;
    }
  },

  async getAll<T extends Record<string, unknown>>(key: string): Promise<T | null> {
    try {
      return await redis.hgetall<T>(key);
    } catch (error) {
      console.error(`Redis HGETALL failed for ${key}:`, error);
      return null;
    }
  },

  async incr(key: string, field: string, increment = 1): Promise<number> {
    try {
      return await redis.hincrby(key, field, increment);
    } catch (error) {
      console.error(`Redis HINCRBY failed for ${key}:${field}:`, error);
      return 0;
    }
  },
};

// Export Redis client for advanced operations
export { redis };
