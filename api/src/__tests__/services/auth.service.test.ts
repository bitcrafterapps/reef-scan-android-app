// ============================================================================
// Auth Service Unit Tests
// Tests for JWT generation, verification, and device management
// ============================================================================

import {
  generateAccessToken,
  generateRefreshToken,
  generateTokenPair,
  verifyAccessToken,
  verifyRefreshToken,
  registerDevice,
  findDeviceByUuid,
  incrementTokenVersion,
  refreshTokens,
  TokenError,
} from '../../services/auth.service';
import type { Device } from '../../types';

describe('Auth Service', () => {
  // -------------------------------------------------------------------------
  // Test Fixtures
  // -------------------------------------------------------------------------

  const mockDevice: Device = {
    id: 'device-123',
    device_uuid: '550e8400-e29b-41d4-a716-446655440000',
    platform: 'ios',
    app_version: '1.0.0',
    created_at: new Date(),
    last_seen_at: new Date(),
    refresh_token: null,
    token_version: 1,
    tier: 'free',
    subscription_id: null,
    is_blocked: false,
    block_reason: null,
    metadata: {},
  };

  const premiumDevice: Device = {
    ...mockDevice,
    id: 'device-456',
    device_uuid: '550e8400-e29b-41d4-a716-446655440001',
    tier: 'premium',
    subscription_id: 'sub_123',
  };

  // -------------------------------------------------------------------------
  // JWT Token Generation Tests
  // -------------------------------------------------------------------------

  describe('generateAccessToken', () => {
    it('should generate a valid JWT token', () => {
      const token = generateAccessToken(mockDevice);

      expect(token).toBeDefined();
      expect(typeof token).toBe('string');
      expect(token.split('.')).toHaveLength(3); // JWT has 3 parts
    });

    it('should include correct payload for free tier', () => {
      const token = generateAccessToken(mockDevice);
      const payload = verifyAccessToken(token);

      expect(payload.sub).toBe(mockDevice.device_uuid);
      expect(payload.platform).toBe('ios');
      expect(payload.tier).toBe('free');
      expect(payload.daily_limit).toBe(3); // Free tier limit
      expect(payload.subscription_id).toBeNull();
    });

    it('should include correct payload for premium tier', () => {
      const token = generateAccessToken(premiumDevice);
      const payload = verifyAccessToken(token);

      expect(payload.tier).toBe('premium');
      expect(payload.daily_limit).toBe(20); // Premium tier limit
      expect(payload.subscription_id).toBe('sub_123');
    });

    it('should set expiration time', () => {
      const token = generateAccessToken(mockDevice);
      const payload = verifyAccessToken(token);

      expect(payload.exp).toBeDefined();
      expect(payload.iat).toBeDefined();
      // Token should expire in ~1 hour (3600 seconds)
      expect(payload.exp - payload.iat).toBe(3600);
    });
  });

  describe('generateRefreshToken', () => {
    it('should generate a valid refresh token', () => {
      const token = generateRefreshToken(mockDevice);

      expect(token).toBeDefined();
      expect(typeof token).toBe('string');
      expect(token.split('.')).toHaveLength(3);
    });

    it('should include token version for revocation', () => {
      const token = generateRefreshToken(mockDevice);
      const payload = verifyRefreshToken(token);

      expect(payload.sub).toBe(mockDevice.device_uuid);
      expect(payload.version).toBe(1);
    });
  });

  describe('generateTokenPair', () => {
    it('should return both tokens with correct format', () => {
      const tokens = generateTokenPair(mockDevice);

      expect(tokens.access_token).toBeDefined();
      expect(tokens.refresh_token).toBeDefined();
      expect(tokens.token_type).toBe('Bearer');
      expect(tokens.expires_in).toBe(3600);
    });
  });

  // -------------------------------------------------------------------------
  // JWT Token Verification Tests
  // -------------------------------------------------------------------------

  describe('verifyAccessToken', () => {
    it('should verify a valid token', () => {
      const token = generateAccessToken(mockDevice);
      const payload = verifyAccessToken(token);

      expect(payload.sub).toBe(mockDevice.device_uuid);
    });

    it('should throw TokenError for invalid token', () => {
      expect(() => verifyAccessToken('invalid-token')).toThrow(TokenError);
    });

    it('should throw TokenError with INVALID_TOKEN code', () => {
      try {
        verifyAccessToken('invalid-token');
        fail('Should have thrown');
      } catch (error) {
        expect(error).toBeInstanceOf(TokenError);
        expect((error as TokenError).code).toBe('INVALID_TOKEN');
      }
    });

    it('should throw TokenError for tampered token', () => {
      const token = generateAccessToken(mockDevice);
      const tamperedToken = token.slice(0, -5) + 'xxxxx';

      expect(() => verifyAccessToken(tamperedToken)).toThrow(TokenError);
    });
  });

  describe('verifyRefreshToken', () => {
    it('should verify a valid refresh token', () => {
      const token = generateRefreshToken(mockDevice);
      const payload = verifyRefreshToken(token);

      expect(payload.sub).toBe(mockDevice.device_uuid);
      expect(payload.version).toBe(1);
    });

    it('should throw TokenError for access token used as refresh', () => {
      const accessToken = generateAccessToken(mockDevice);

      expect(() => verifyRefreshToken(accessToken)).toThrow(TokenError);
    });
  });

  // -------------------------------------------------------------------------
  // Device Registration Tests
  // -------------------------------------------------------------------------

  describe('registerDevice', () => {
    it('should create a new device', async () => {
      const result = await registerDevice(
        '550e8400-e29b-41d4-a716-446655440099',
        'android',
        '2.0.0'
      );

      expect(result.isNew).toBe(true);
      expect(result.device).toBeDefined();
      expect(result.device.platform).toBe('android');
      expect(result.device.app_version).toBe('2.0.0');
      expect(result.device.tier).toBe('free');
    });

    it('should return existing device on re-registration', async () => {
      const deviceUuid = '550e8400-e29b-41d4-a716-446655440088';

      // First registration
      const first = await registerDevice(deviceUuid, 'ios', '1.0.0');
      expect(first.isNew).toBe(true);

      // Second registration
      const second = await registerDevice(deviceUuid, 'ios', '1.1.0');
      expect(second.isNew).toBe(false);
      expect(second.device.app_version).toBe('1.1.0'); // Updated
    });
  });

  describe('findDeviceByUuid', () => {
    it('should find an existing device', async () => {
      const deviceUuid = '550e8400-e29b-41d4-a716-446655440077';
      await registerDevice(deviceUuid, 'ios', '1.0.0');

      const found = await findDeviceByUuid(deviceUuid);

      expect(found).not.toBeNull();
      expect(found?.device_uuid).toBe(deviceUuid);
    });

    it('should return null for non-existent device', async () => {
      const found = await findDeviceByUuid('non-existent-uuid');

      expect(found).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // Token Refresh Tests
  // -------------------------------------------------------------------------

  describe('refreshTokens', () => {
    it('should refresh tokens for valid refresh token', async () => {
      // Register device first
      const deviceUuid = '550e8400-e29b-41d4-a716-446655440066';
      const { device } = await registerDevice(deviceUuid, 'ios', '1.0.0');

      // Generate refresh token
      const refreshToken = generateRefreshToken(device);

      // Refresh tokens
      const newTokens = await refreshTokens(refreshToken);

      expect(newTokens.access_token).toBeDefined();
      expect(newTokens.refresh_token).toBeDefined();
      expect(newTokens.token_type).toBe('Bearer');
    });

    it('should throw TokenError for invalid refresh token', async () => {
      await expect(refreshTokens('invalid-token')).rejects.toThrow(TokenError);
    });

    it('should throw TokenError for revoked token (version mismatch)', async () => {
      // Register device
      const deviceUuid = '550e8400-e29b-41d4-a716-446655440055';
      const { device } = await registerDevice(deviceUuid, 'ios', '1.0.0');

      // Generate refresh token with version 1
      const refreshToken = generateRefreshToken(device);

      // Increment token version (simulate revocation)
      await incrementTokenVersion(deviceUuid);

      // Try to refresh - should fail because version changed
      await expect(refreshTokens(refreshToken)).rejects.toThrow(TokenError);
    });
  });

  // -------------------------------------------------------------------------
  // Token Version Tests
  // -------------------------------------------------------------------------

  describe('incrementTokenVersion', () => {
    it('should increment token version', async () => {
      const deviceUuid = '550e8400-e29b-41d4-a716-446655440044';
      await registerDevice(deviceUuid, 'ios', '1.0.0');

      const newVersion = await incrementTokenVersion(deviceUuid);

      expect(newVersion).toBe(2);
    });
  });
});
