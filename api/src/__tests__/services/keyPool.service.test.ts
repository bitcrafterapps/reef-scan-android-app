// ============================================================================
// Key Pool Service Unit Tests
// Tests for API key selection and health monitoring
// ============================================================================

import {
  initializeKeyPool,
  getKeys,
  selectKey,
  recordSuccess,
  recordFailure,
  getKeyPoolMetrics,
  hasAvailableCapacity,
} from '../../services/keyPool.service';
import * as redis from '../../services/redis.service';

// Access mock store for direct manipulation
const mockStore = (redis as unknown as { __mockStore: Map<string, { value: unknown; expiry?: number }> }).__mockStore;

describe('Key Pool Service', () => {
  // -------------------------------------------------------------------------
  // Initialization Tests
  // -------------------------------------------------------------------------

  describe('initializeKeyPool', () => {
    it('should initialize without error', () => {
      expect(() => initializeKeyPool()).not.toThrow();
    });

    it('should be idempotent (multiple calls safe)', () => {
      initializeKeyPool();
      initializeKeyPool();
      initializeKeyPool();

      const keys = getKeys();
      expect(keys.length).toBeGreaterThan(0);
    });
  });

  describe('getKeys', () => {
    it('should return configured keys', () => {
      const keys = getKeys();

      // We have 2 test keys in setup.ts
      expect(keys.length).toBe(2);
    });

    it('should have valid key configuration', () => {
      const keys = getKeys();

      keys.forEach((key) => {
        expect(key.id).toBeDefined();
        expect(key.key).toBeDefined();
        expect(key.rpmLimit).toBeGreaterThan(0);
        expect(key.tier).toBeDefined();
      });
    });
  });

  // -------------------------------------------------------------------------
  // Key Selection Tests
  // -------------------------------------------------------------------------

  describe('selectKey', () => {
    it('should select an available key', async () => {
      const key = await selectKey();

      expect(key).not.toBeNull();
      expect(key?.id).toBeDefined();
      expect(key?.key).toBeDefined();
    });

    it('should return key with lowest RPM', async () => {
      const currentMinute = Math.floor(Date.now() / 60000);

      // Set key 1 to have higher RPM
      mockStore.set(`keypool:rpm:gemini_1:${currentMinute}`, { value: 30 });
      mockStore.set(`keypool:rpm:gemini_2:${currentMinute}`, { value: 10 });

      const key = await selectKey();

      // Should select gemini_2 (lower RPM)
      expect(key?.id).toBe('gemini_2');
    });

    it('should skip keys in cooldown', async () => {
      const cooldownUntil = new Date(Date.now() + 60000).toISOString();

      // Put key 1 in cooldown
      mockStore.set('keypool:state:gemini_1', {
        value: { inCooldown: true, cooldownUntil },
      });

      const key = await selectKey();

      // Should not select key in cooldown
      expect(key?.id).toBe('gemini_2');
    });

    it('should skip keys at RPM limit', async () => {
      const currentMinute = Math.floor(Date.now() / 60000);

      // Set key 1 to RPM limit (60)
      mockStore.set(`keypool:rpm:gemini_1:${currentMinute}`, { value: 60 });

      const key = await selectKey();

      // Should skip key at limit
      expect(key?.id).toBe('gemini_2');
    });

    it('should return null when all keys unavailable', async () => {
      const currentMinute = Math.floor(Date.now() / 60000);
      const cooldownUntil = new Date(Date.now() + 60000).toISOString();

      // Put key 1 in cooldown
      mockStore.set('keypool:state:gemini_1', {
        value: { inCooldown: true, cooldownUntil },
      });

      // Set key 2 to RPM limit
      mockStore.set(`keypool:rpm:gemini_2:${currentMinute}`, { value: 60 });

      const key = await selectKey();

      expect(key).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // Usage Tracking Tests
  // -------------------------------------------------------------------------

  describe('recordSuccess', () => {
    it('should increment RPM counter', async () => {
      const keyId = 'gemini_1';
      const currentMinute = Math.floor(Date.now() / 60000);
      const rpmKey = `keypool:rpm:${keyId}:${currentMinute}`;

      await recordSuccess(keyId);

      const stored = mockStore.get(rpmKey);
      expect(stored?.value).toBe(1);
    });

    it('should increment daily counter', async () => {
      const keyId = 'gemini_1';
      const dailyKey = `keypool:daily:${keyId}`;

      await recordSuccess(keyId);

      const stored = mockStore.get(dailyKey);
      expect(stored?.value).toBe(1);
    });

    it('should increment success counter for error rate calculation', async () => {
      const keyId = 'gemini_1';
      const successKey = `keypool:success:${keyId}`;

      await recordSuccess(keyId);
      await recordSuccess(keyId);

      const stored = mockStore.get(successKey);
      expect(stored?.value).toBe(2);
    });
  });

  describe('recordFailure', () => {
    it('should increment error counter', async () => {
      const keyId = 'gemini_1';
      const errorKey = `keypool:errors:${keyId}`;

      await recordFailure(keyId);

      const stored = mockStore.get(errorKey);
      expect(stored?.value).toBe(1);
    });

    it('should put key in cooldown on 429 status', async () => {
      const keyId = 'gemini_1';
      const stateKey = `keypool:state:${keyId}`;

      await recordFailure(keyId, 429);

      const stored = mockStore.get(stateKey);
      expect((stored?.value as { inCooldown: boolean }).inCooldown).toBe(true);
    });

    it('should not put key in cooldown for other errors', async () => {
      const keyId = 'gemini_2';
      const stateKey = `keypool:state:${keyId}`;

      await recordFailure(keyId, 500);

      const stored = mockStore.get(stateKey);
      // Should not have cooldown state set
      expect(stored?.value).toBeUndefined();
    });
  });

  // -------------------------------------------------------------------------
  // Metrics Tests
  // -------------------------------------------------------------------------

  describe('getKeyPoolMetrics', () => {
    it('should return pool metrics', async () => {
      const metrics = await getKeyPoolMetrics();

      expect(metrics.total_keys).toBe(2);
      expect(metrics.available_keys).toBeDefined();
      expect(metrics.keys_in_cooldown).toBeDefined();
      expect(metrics.total_rpm).toBeDefined();
      expect(metrics.keys).toHaveLength(2);
    });

    it('should include per-key metrics', async () => {
      await recordSuccess('gemini_1');
      await recordSuccess('gemini_1');

      const metrics = await getKeyPoolMetrics();
      const key1Metrics = metrics.keys.find((k) => k.id === 'gemini_1');

      expect(key1Metrics).toBeDefined();
      expect(key1Metrics?.current_rpm).toBeGreaterThan(0);
      expect(key1Metrics?.requests_today).toBeGreaterThan(0);
    });

    it('should track keys in cooldown', async () => {
      const cooldownUntil = new Date(Date.now() + 60000).toISOString();

      mockStore.set('keypool:state:gemini_1', {
        value: { inCooldown: true, cooldownUntil },
      });

      const metrics = await getKeyPoolMetrics();

      expect(metrics.keys_in_cooldown).toBe(1);
    });

    it('should calculate error rate per key', async () => {
      // Record successes and failures
      mockStore.set('keypool:success:gemini_1', { value: 90 });
      mockStore.set('keypool:errors:gemini_1', { value: 10 });

      const metrics = await getKeyPoolMetrics();
      const key1Metrics = metrics.keys.find((k) => k.id === 'gemini_1');

      expect(key1Metrics?.error_rate).toBe(0.1); // 10%
    });
  });

  describe('hasAvailableCapacity', () => {
    it('should return true when keys available', async () => {
      const hasCapacity = await hasAvailableCapacity();

      expect(hasCapacity).toBe(true);
    });

    it('should return false when all keys unavailable', async () => {
      const currentMinute = Math.floor(Date.now() / 60000);
      const cooldownUntil = new Date(Date.now() + 60000).toISOString();

      // Put key 1 in cooldown
      mockStore.set('keypool:state:gemini_1', {
        value: { inCooldown: true, cooldownUntil },
      });

      // Set key 2 to RPM limit
      mockStore.set(`keypool:rpm:gemini_2:${currentMinute}`, { value: 60 });

      const hasCapacity = await hasAvailableCapacity();

      expect(hasCapacity).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // Error Rate Tests
  // -------------------------------------------------------------------------

  describe('error rate handling', () => {
    it('should skip keys with high error rate', async () => {
      // Set high error rate for key 1 (>5%)
      mockStore.set('keypool:success:gemini_1', { value: 90 });
      mockStore.set('keypool:errors:gemini_1', { value: 10 }); // 10% error rate

      const key = await selectKey();

      // Should select key 2 instead due to high error rate on key 1
      expect(key?.id).toBe('gemini_2');
    });

    it('should not penalize keys with few requests', async () => {
      // Set high error rate but low total requests
      mockStore.set('keypool:success:gemini_1', { value: 5 });
      mockStore.set('keypool:errors:gemini_1', { value: 1 }); // 16% error rate but only 6 requests

      // Clear key 2 RPM to make both keys equally available
      mockStore.delete(`keypool:rpm:gemini_1:${Math.floor(Date.now() / 60000)}`);
      mockStore.delete(`keypool:rpm:gemini_2:${Math.floor(Date.now() / 60000)}`);

      const key = await selectKey();

      // Key 1 should still be available (below MIN_REQUESTS_FOR_ERROR_RATE)
      // Either key could be selected since both have 0 RPM
      expect(key).not.toBeNull();
    });
  });
});
