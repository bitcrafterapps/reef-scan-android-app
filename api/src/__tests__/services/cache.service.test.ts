// ============================================================================
// Cache Service Unit Tests
// Tests for image hash caching and request idempotency
// ============================================================================

import {
  calculateImageHash,
  getCachedResult,
  cacheResult,
  getCacheHitCount,
  getIdempotentResult,
  setIdempotentResult,
  isRequestProcessed,
  invalidateImageCache,
  getCacheStats,
} from '../../services/cache.service';
import * as redis from '../../services/redis.service';
import type { ScanResult } from '../../types';

// Access mock store for direct manipulation
const mockStore = (redis as unknown as { __mockStore: Map<string, { value: unknown; expiry?: number }> }).__mockStore;

describe('Cache Service', () => {
  // -------------------------------------------------------------------------
  // Test Fixtures
  // -------------------------------------------------------------------------

  const mockScanResult: ScanResult = {
    request_id: 'req-123',
    tank_health: 'Good',
    summary: 'Tank looks healthy with some clownfish identified.',
    identifications: [
      {
        name: 'Clownfish',
        category: 'fish',
        confidence: 0.95,
        is_problem: false,
        severity: null,
        description: 'Amphiprion ocellaris - healthy specimen',
      },
    ],
    recommendations: ['Maintain current water parameters'],
    usage: {
      requests_today: 1,
      daily_limit: 3,
      reset_at: new Date().toISOString(),
    },
  };

  // -------------------------------------------------------------------------
  // calculateImageHash Tests
  // -------------------------------------------------------------------------

  describe('calculateImageHash', () => {
    it('should calculate SHA-256 hash of image data', () => {
      const imageData = 'base64encodedimagedata';
      const hash = calculateImageHash(imageData);

      expect(hash).toBeDefined();
      expect(typeof hash).toBe('string');
      expect(hash).toHaveLength(64); // SHA-256 produces 64 hex characters
    });

    it('should produce consistent hashes for same input', () => {
      const imageData = 'test-image-data-12345';

      const hash1 = calculateImageHash(imageData);
      const hash2 = calculateImageHash(imageData);

      expect(hash1).toBe(hash2);
    });

    it('should produce different hashes for different inputs', () => {
      const hash1 = calculateImageHash('image-data-1');
      const hash2 = calculateImageHash('image-data-2');

      expect(hash1).not.toBe(hash2);
    });

    it('should handle empty string', () => {
      const hash = calculateImageHash('');

      // SHA-256 of empty string
      expect(hash).toBe('e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855');
    });
  });

  // -------------------------------------------------------------------------
  // getCachedResult Tests
  // -------------------------------------------------------------------------

  describe('getCachedResult', () => {
    it('should return null for cache miss', async () => {
      const result = await getCachedResult('nonexistent-hash', 'fish_id');

      expect(result).toBeNull();
    });

    it('should return cached result on cache hit', async () => {
      const imageHash = 'cached-hash-123';
      const mode = 'fish_id';
      const cacheKey = `cache:image:${imageHash}:${mode}`;

      mockStore.set(cacheKey, { value: mockScanResult });

      const result = await getCachedResult(imageHash, mode);

      expect(result).toEqual(mockScanResult);
    });

    it('should increment hit counter on cache hit', async () => {
      const imageHash = 'hit-counter-test';
      const mode = 'comprehensive';
      const cacheKey = `cache:image:${imageHash}:${mode}`;
      const hitKey = `cache:hits:${imageHash}`;

      mockStore.set(cacheKey, { value: mockScanResult });

      await getCachedResult(imageHash, mode);
      await getCachedResult(imageHash, mode);

      const hits = mockStore.get(hitKey);
      expect(hits?.value).toBe(2);
    });

    it('should return different results for different modes', async () => {
      const imageHash = 'multi-mode-test';
      const fishResult = { ...mockScanResult, summary: 'Fish identified' };
      const coralResult = { ...mockScanResult, summary: 'Coral identified' };

      mockStore.set(`cache:image:${imageHash}:fish_id`, { value: fishResult });
      mockStore.set(`cache:image:${imageHash}:coral_id`, { value: coralResult });

      const fish = await getCachedResult(imageHash, 'fish_id');
      const coral = await getCachedResult(imageHash, 'coral_id');

      expect(fish?.summary).toBe('Fish identified');
      expect(coral?.summary).toBe('Coral identified');
    });
  });

  // -------------------------------------------------------------------------
  // cacheResult Tests
  // -------------------------------------------------------------------------

  describe('cacheResult', () => {
    it('should store result in cache', async () => {
      const imageHash = 'new-cache-entry';
      const mode = 'fish_id';

      const success = await cacheResult(imageHash, mode, mockScanResult);

      expect(success).toBe(true);

      const cached = await getCachedResult(imageHash, mode);
      expect(cached).toEqual(mockScanResult);
    });

    it('should set TTL on cached result', async () => {
      const imageHash = 'ttl-test-hash';
      const mode = 'comprehensive';
      const cacheKey = `cache:image:${imageHash}:${mode}`;

      await cacheResult(imageHash, mode, mockScanResult);

      const stored = mockStore.get(cacheKey);
      expect(stored?.expiry).toBeDefined();
      // TTL should be set in the future
      expect(stored?.expiry).toBeGreaterThan(Date.now());
    });
  });

  // -------------------------------------------------------------------------
  // getCacheHitCount Tests
  // -------------------------------------------------------------------------

  describe('getCacheHitCount', () => {
    it('should return 0 for uncached image', async () => {
      const count = await getCacheHitCount('never-cached');

      expect(count).toBe(0);
    });

    it('should return accurate hit count', async () => {
      const imageHash = 'hit-count-image';
      mockStore.set(`cache:hits:${imageHash}`, { value: 42 });

      const count = await getCacheHitCount(imageHash);

      expect(count).toBe(42);
    });
  });

  // -------------------------------------------------------------------------
  // Idempotency Tests
  // -------------------------------------------------------------------------

  describe('getIdempotentResult', () => {
    it('should return null for new request', async () => {
      const result = await getIdempotentResult('new-request-id');

      expect(result).toBeNull();
    });

    it('should return stored result for processed request', async () => {
      const requestId = 'processed-request-id';
      mockStore.set(`idempotency:${requestId}`, { value: mockScanResult });

      const result = await getIdempotentResult(requestId);

      expect(result).toEqual(mockScanResult);
    });
  });

  describe('setIdempotentResult', () => {
    it('should store result for request', async () => {
      const requestId = 'new-idempotent-request';

      const success = await setIdempotentResult(requestId, mockScanResult);

      expect(success).toBe(true);

      const stored = await getIdempotentResult(requestId);
      expect(stored).toEqual(mockScanResult);
    });

    it('should set TTL on idempotent result', async () => {
      const requestId = 'ttl-idempotent-request';

      await setIdempotentResult(requestId, mockScanResult);

      const stored = mockStore.get(`idempotency:${requestId}`);
      expect(stored?.expiry).toBeDefined();
    });
  });

  describe('isRequestProcessed', () => {
    it('should return false for new request', async () => {
      const processed = await isRequestProcessed('brand-new-request');

      expect(processed).toBe(false);
    });

    it('should return true for processed request', async () => {
      const requestId = 'already-processed';
      mockStore.set(`idempotency:${requestId}`, { value: mockScanResult });

      const processed = await isRequestProcessed(requestId);

      expect(processed).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // Cache Invalidation Tests
  // -------------------------------------------------------------------------

  describe('invalidateImageCache', () => {
    it('should remove cached results for all modes', async () => {
      const imageHash = 'invalidate-test';

      // Cache results for multiple modes
      mockStore.set(`cache:image:${imageHash}:fish_id`, { value: mockScanResult });
      mockStore.set(`cache:image:${imageHash}:coral_id`, { value: mockScanResult });
      mockStore.set(`cache:image:${imageHash}:comprehensive`, { value: mockScanResult });
      mockStore.set(`cache:hits:${imageHash}`, { value: 10 });

      await invalidateImageCache(imageHash);

      expect(await getCachedResult(imageHash, 'fish_id')).toBeNull();
      expect(await getCachedResult(imageHash, 'coral_id')).toBeNull();
      expect(await getCachedResult(imageHash, 'comprehensive')).toBeNull();
      expect(await getCacheHitCount(imageHash)).toBe(0);
    });
  });

  // -------------------------------------------------------------------------
  // getCacheStats Tests
  // -------------------------------------------------------------------------

  describe('getCacheStats', () => {
    it('should return cache configuration', async () => {
      const stats = await getCacheStats();

      expect(stats.enabled).toBeDefined();
      expect(stats.ttl_days).toBeDefined();
      expect(stats.idempotency_ttl_hours).toBeDefined();
    });

    it('should return correct TTL values', async () => {
      const stats = await getCacheStats();

      expect(typeof stats.ttl_days).toBe('number');
      expect(typeof stats.idempotency_ttl_hours).toBe('number');
      expect(stats.ttl_days).toBeGreaterThan(0);
      expect(stats.idempotency_ttl_hours).toBeGreaterThan(0);
    });
  });
});
