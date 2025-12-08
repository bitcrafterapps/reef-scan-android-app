// ============================================================================
// Cache Service
// Image hash caching and request idempotency
// ============================================================================

import { createHash } from 'crypto';
import * as redis from './redis.service';
import config from '../config';
import type { ScanResult, AnalysisMode } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Redis Key Patterns
// -----------------------------------------------------------------------------

const KEYS = {
  // Cache by image hash + mode
  imageCache: (hash: string, mode: string) => `cache:image:${hash}:${mode}`,
  // Idempotency key for request deduplication
  idempotency: (requestId: string) => `idempotency:${requestId}`,
  // Cache hit counter
  cacheHits: (hash: string) => `cache:hits:${hash}`,
};

// -----------------------------------------------------------------------------
// Image Hash Calculation
// -----------------------------------------------------------------------------

/**
 * Calculate SHA-256 hash of image data
 */
export function calculateImageHash(imageData: string): string {
  return createHash('sha256').update(imageData).digest('hex');
}

// -----------------------------------------------------------------------------
// Result Caching
// -----------------------------------------------------------------------------

/**
 * Get cached result for an image
 */
export async function getCachedResult(
  imageHash: string,
  mode: AnalysisMode
): Promise<ScanResult | null> {
  if (!config.features.enableImageCaching) {
    return null;
  }

  try {
    const key = KEYS.imageCache(imageHash, mode);
    const cached = await redis.get<ScanResult>(key);

    if (cached) {
      // Increment hit counter
      await redis.incr(KEYS.cacheHits(imageHash));

      logger.debug('Cache hit', {
        image_hash: imageHash.substring(0, 16),
        mode,
      });

      return cached;
    }

    return null;
  } catch (error) {
    logger.error('Cache get failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    return null;
  }
}

/**
 * Store result in cache
 */
export async function cacheResult(
  imageHash: string,
  mode: AnalysisMode,
  result: ScanResult
): Promise<boolean> {
  if (!config.features.enableImageCaching) {
    return false;
  }

  try {
    const key = KEYS.imageCache(imageHash, mode);
    const ttlSeconds = config.cache.imageTtlDays * 24 * 60 * 60;

    await redis.set(key, result, ttlSeconds);

    logger.debug('Result cached', {
      image_hash: imageHash.substring(0, 16),
      mode,
      ttl_days: config.cache.imageTtlDays,
    });

    return true;
  } catch (error) {
    logger.error('Cache set failed', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    return false;
  }
}

/**
 * Get cache hit count for an image
 */
export async function getCacheHitCount(imageHash: string): Promise<number> {
  return await redis.get<number>(KEYS.cacheHits(imageHash)) || 0;
}

// -----------------------------------------------------------------------------
// Request Idempotency
// -----------------------------------------------------------------------------

/**
 * Check if a request has already been processed
 * Returns the cached result if available
 */
export async function getIdempotentResult(
  requestId: string
): Promise<ScanResult | null> {
  try {
    const key = KEYS.idempotency(requestId);
    return await redis.get<ScanResult>(key);
  } catch (error) {
    logger.error('Idempotency check failed', {
      request_id: requestId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    return null;
  }
}

/**
 * Store result for idempotency
 */
export async function setIdempotentResult(
  requestId: string,
  result: ScanResult
): Promise<boolean> {
  try {
    const key = KEYS.idempotency(requestId);
    const ttlSeconds = config.cache.idempotencyTtlHours * 60 * 60;

    await redis.set(key, result, ttlSeconds);
    return true;
  } catch (error) {
    logger.error('Idempotency set failed', {
      request_id: requestId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
    return false;
  }
}

/**
 * Check if request ID exists (for deduplication without result)
 */
export async function isRequestProcessed(requestId: string): Promise<boolean> {
  try {
    const key = KEYS.idempotency(requestId);
    return await redis.exists(key);
  } catch {
    return false;
  }
}

// -----------------------------------------------------------------------------
// Cache Management
// -----------------------------------------------------------------------------

/**
 * Invalidate cache for a specific image
 */
export async function invalidateImageCache(imageHash: string): Promise<void> {
  const modes: AnalysisMode[] = ['comprehensive', 'fish_id', 'coral_id', 'algae_id', 'pest_id'];

  for (const mode of modes) {
    const key = KEYS.imageCache(imageHash, mode);
    await redis.del(key);
  }

  await redis.del(KEYS.cacheHits(imageHash));

  logger.info('Image cache invalidated', {
    image_hash: imageHash.substring(0, 16),
  });
}

/**
 * Get cache statistics
 */
export async function getCacheStats(): Promise<{
  enabled: boolean;
  ttl_days: number;
  idempotency_ttl_hours: number;
}> {
  return {
    enabled: config.features.enableImageCaching,
    ttl_days: config.cache.imageTtlDays,
    idempotency_ttl_hours: config.cache.idempotencyTtlHours,
  };
}
