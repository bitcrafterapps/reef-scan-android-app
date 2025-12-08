// ============================================================================
// Rate Limit Service Unit Tests
// Tests for rate limiting logic across tiers
// ============================================================================

import {
  checkRateLimit,
  checkIpRateLimit,
  getDailyUsage,
  getRateLimitInfo,
  resetDailyUsage,
  formatResetTime,
} from '../../services/rateLimit.service';
import * as redis from '../../services/redis.service';

// Access mock store for direct manipulation
const mockStore = (redis as unknown as { __mockStore: Map<string, { value: unknown; expiry?: number }> }).__mockStore;

describe('Rate Limit Service', () => {
  // -------------------------------------------------------------------------
  // Test Fixtures
  // -------------------------------------------------------------------------

  const testDeviceId = 'device-rate-test-123';
  const testIp = '192.168.1.100';

  // -------------------------------------------------------------------------
  // checkRateLimit Tests
  // -------------------------------------------------------------------------

  describe('checkRateLimit', () => {
    describe('free tier', () => {
      it('should allow request when under daily limit', async () => {
        const result = await checkRateLimit(testDeviceId, 'free');

        expect(result.allowed).toBe(true);
        expect(result.tier).toBe('free');
        expect(result.limit).toBe(3); // Free tier limit
        expect(result.remaining).toBe(2); // 3 - 1 = 2
      });

      it('should block request when daily limit is reached', async () => {
        // Set usage to limit (3 for free tier)
        mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 3 });

        const result = await checkRateLimit(testDeviceId, 'free');

        expect(result.allowed).toBe(false);
        expect(result.remaining).toBe(0);
        expect(result.upgrade_url).toBe('https://reefscan.app/upgrade');
      });

      it('should decrement remaining count on each request', async () => {
        // First request
        const result1 = await checkRateLimit(testDeviceId, 'free');
        expect(result1.remaining).toBe(2);

        // Second request
        const result2 = await checkRateLimit(testDeviceId, 'free');
        expect(result2.remaining).toBe(1);

        // Third request (last allowed)
        const result3 = await checkRateLimit(testDeviceId, 'free');
        expect(result3.remaining).toBe(0);

        // Fourth request should be blocked
        const result4 = await checkRateLimit(testDeviceId, 'free');
        expect(result4.allowed).toBe(false);
      });
    });

    describe('premium tier', () => {
      it('should allow request when under daily limit', async () => {
        const result = await checkRateLimit(testDeviceId, 'premium');

        expect(result.allowed).toBe(true);
        expect(result.tier).toBe('premium');
        expect(result.limit).toBe(20); // Premium tier limit
        expect(result.remaining).toBe(19); // 20 - 1 = 19
      });

      it('should block request when daily limit is reached', async () => {
        // Set usage to limit (20 for premium tier)
        mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 20 });

        const result = await checkRateLimit(testDeviceId, 'premium');

        expect(result.allowed).toBe(false);
        expect(result.remaining).toBe(0);
        // Premium users don't get upgrade URL
        expect(result.upgrade_url).toBeUndefined();
      });

      it('should have higher limit than free tier', async () => {
        const freeResult = await checkRateLimit(testDeviceId, 'free');
        mockStore.clear(); // Reset for premium test
        const premiumResult = await checkRateLimit(testDeviceId, 'premium');

        expect(premiumResult.limit).toBeGreaterThan(freeResult.limit);
      });
    });

    describe('per-minute rate limiting', () => {
      it('should block rapid requests exceeding per-minute limit', async () => {
        const currentMinute = Math.floor(Date.now() / 60000);
        const minuteKey = `ratelimit:minute:${testDeviceId}:${currentMinute}`;

        // Set minute usage to limit (5 per minute)
        mockStore.set(minuteKey, { value: 5 });

        const result = await checkRateLimit(testDeviceId, 'premium');

        expect(result.allowed).toBe(false);
      });
    });

    describe('reset timestamp', () => {
      it('should return reset_at as unix timestamp', async () => {
        const result = await checkRateLimit(testDeviceId, 'free');

        expect(result.reset_at).toBeDefined();
        expect(typeof result.reset_at).toBe('number');
        expect(result.reset_at).toBeGreaterThan(Math.floor(Date.now() / 1000));
      });

      it('should reset at midnight UTC', async () => {
        const result = await checkRateLimit(testDeviceId, 'free');
        const resetDate = new Date(result.reset_at * 1000);

        expect(resetDate.getUTCHours()).toBe(0);
        expect(resetDate.getUTCMinutes()).toBe(0);
        expect(resetDate.getUTCSeconds()).toBe(0);
      });
    });
  });

  // -------------------------------------------------------------------------
  // checkIpRateLimit Tests
  // -------------------------------------------------------------------------

  describe('checkIpRateLimit', () => {
    it('should allow request when under IP limit', async () => {
      const result = await checkIpRateLimit(testIp);

      expect(result).toBe(true);
    });

    it('should block request when IP limit is reached', async () => {
      const currentHour = Math.floor(Date.now() / 3600000);
      const ipKey = `ratelimit:ip:${testIp}:${currentHour}`;

      // Set IP usage to limit (1000 per hour default)
      mockStore.set(ipKey, { value: 1000 });

      const result = await checkIpRateLimit(testIp);

      expect(result).toBe(false);
    });

    it('should increment IP counter on each request', async () => {
      const currentHour = Math.floor(Date.now() / 3600000);
      const ipKey = `ratelimit:ip:${testIp}:${currentHour}`;

      await checkIpRateLimit(testIp);
      let stored = mockStore.get(ipKey);
      expect(stored?.value).toBe(1);

      await checkIpRateLimit(testIp);
      stored = mockStore.get(ipKey);
      expect(stored?.value).toBe(2);
    });
  });

  // -------------------------------------------------------------------------
  // getDailyUsage Tests
  // -------------------------------------------------------------------------

  describe('getDailyUsage', () => {
    it('should return 0 for new device', async () => {
      const usage = await getDailyUsage('new-device-id');

      expect(usage).toBe(0);
    });

    it('should return current usage for device', async () => {
      mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 5 });

      const usage = await getDailyUsage(testDeviceId);

      expect(usage).toBe(5);
    });
  });

  // -------------------------------------------------------------------------
  // getRateLimitInfo Tests
  // -------------------------------------------------------------------------

  describe('getRateLimitInfo', () => {
    it('should return rate limit info without incrementing', async () => {
      const result = await getRateLimitInfo(testDeviceId, 'free');

      expect(result.limit).toBe(3);
      expect(result.remaining).toBe(3); // Should not increment
      expect(result.tier).toBe('free');
    });

    it('should show allowed=false when limit reached', async () => {
      mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 3 });

      const result = await getRateLimitInfo(testDeviceId, 'free');

      expect(result.allowed).toBe(false);
      expect(result.remaining).toBe(0);
      expect(result.upgrade_url).toBe('https://reefscan.app/upgrade');
    });

    it('should return correct info for premium tier', async () => {
      mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 5 });

      const result = await getRateLimitInfo(testDeviceId, 'premium');

      expect(result.limit).toBe(20);
      expect(result.remaining).toBe(15); // 20 - 5
      expect(result.tier).toBe('premium');
    });
  });

  // -------------------------------------------------------------------------
  // resetDailyUsage Tests
  // -------------------------------------------------------------------------

  describe('resetDailyUsage', () => {
    it('should reset daily usage to 0', async () => {
      // Set some usage
      mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 5 });

      await resetDailyUsage(testDeviceId);

      const usage = await getDailyUsage(testDeviceId);
      expect(usage).toBe(0);
    });

    it('should return true on successful reset', async () => {
      mockStore.set(`ratelimit:daily:${testDeviceId}`, { value: 5 });

      const result = await resetDailyUsage(testDeviceId);

      expect(result).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // formatResetTime Tests
  // -------------------------------------------------------------------------

  describe('formatResetTime', () => {
    it('should format timestamp as ISO string', () => {
      const timestamp = 1704067200; // 2024-01-01T00:00:00.000Z

      const formatted = formatResetTime(timestamp);

      expect(formatted).toBe('2024-01-01T00:00:00.000Z');
    });
  });
});
