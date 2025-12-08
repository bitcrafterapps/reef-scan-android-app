// ============================================================================
// Authentication Service
// JWT generation, verification, and device management
// ============================================================================

import jwt, { JwtPayload, SignOptions } from 'jsonwebtoken';
import { v4 as uuidv4 } from 'uuid';
import { sql } from '../db';
import config from '../config';
import type { Device, JWTPayload, TokenResponse } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// JWT Token Generation
// -----------------------------------------------------------------------------

/**
 * Generate access token (short-lived)
 */
export function generateAccessToken(device: Device): string {
  const payload: Omit<JWTPayload, 'iat' | 'exp'> = {
    sub: device.device_uuid,
    platform: device.platform,
    tier: device.tier,
    daily_limit: device.tier === 'premium' ? config.rateLimit.premium.daily : config.rateLimit.free.daily,
    subscription_id: device.subscription_id,
  };

  const options: SignOptions = {
    expiresIn: config.jwt.accessTokenExpirySeconds,
    issuer: config.jwt.issuer,
  };

  return jwt.sign(payload, config.jwt.secret, options);
}

/**
 * Generate refresh token (long-lived)
 */
export function generateRefreshToken(device: Device): string {
  const payload = {
    sub: device.device_uuid,
    type: 'refresh',
    version: device.token_version,
  };

  const options: SignOptions = {
    expiresIn: config.jwt.refreshTokenExpirySeconds,
    issuer: config.jwt.issuer,
  };

  return jwt.sign(payload, config.jwt.secret, options);
}

/**
 * Generate both access and refresh tokens
 */
export function generateTokenPair(device: Device): TokenResponse {
  return {
    access_token: generateAccessToken(device),
    refresh_token: generateRefreshToken(device),
    token_type: 'Bearer',
    expires_in: 3600, // 1 hour in seconds
  };
}

// -----------------------------------------------------------------------------
// JWT Token Verification
// -----------------------------------------------------------------------------

/**
 * Verify access token and return payload
 */
export function verifyAccessToken(token: string): JWTPayload {
  try {
    const decoded = jwt.verify(token, config.jwt.secret, {
      issuer: config.jwt.issuer,
    }) as JwtPayload & Omit<JWTPayload, 'iat' | 'exp'>;

    return {
      sub: decoded.sub as string,
      iat: decoded.iat as number,
      exp: decoded.exp as number,
      platform: decoded.platform as 'ios' | 'android',
      tier: decoded.tier as 'free' | 'premium',
      daily_limit: decoded.daily_limit as number,
      subscription_id: decoded.subscription_id as string | null,
    };
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError) {
      throw new TokenError('TOKEN_EXPIRED', 'Access token has expired');
    }
    if (error instanceof jwt.JsonWebTokenError) {
      throw new TokenError('INVALID_TOKEN', 'Invalid access token');
    }
    throw error;
  }
}

/**
 * Verify refresh token and return payload
 */
export function verifyRefreshToken(token: string): { sub: string; version: number } {
  try {
    const decoded = jwt.verify(token, config.jwt.secret, {
      issuer: config.jwt.issuer,
    }) as JwtPayload & { type?: string; version?: number };

    if (decoded.type !== 'refresh') {
      throw new TokenError('INVALID_TOKEN', 'Invalid token type');
    }

    return {
      sub: decoded.sub as string,
      version: decoded.version as number,
    };
  } catch (error) {
    if (error instanceof jwt.TokenExpiredError) {
      throw new TokenError('TOKEN_EXPIRED', 'Refresh token has expired');
    }
    if (error instanceof jwt.JsonWebTokenError) {
      throw new TokenError('INVALID_TOKEN', 'Invalid refresh token');
    }
    throw error;
  }
}

// -----------------------------------------------------------------------------
// Device Management
// -----------------------------------------------------------------------------

/**
 * Register a new device or return existing one
 */
export async function registerDevice(
  deviceUuid: string,
  platform: 'ios' | 'android',
  appVersion: string
): Promise<{ device: Device; isNew: boolean }> {
  // Check if device already exists
  const existingDevice = await findDeviceByUuid(deviceUuid);

  if (existingDevice) {
    // Update last seen and app version
    await sql`
      UPDATE devices
      SET last_seen_at = NOW(),
          app_version = ${appVersion}
      WHERE device_uuid = ${deviceUuid}
    `;

    logger.info('Device re-registered', {
      device_id: existingDevice.id,
      platform,
    });

    return { device: { ...existingDevice, app_version: appVersion }, isNew: false };
  }

  // Create new device
  const id = uuidv4();
  const result = await sql`
    INSERT INTO devices (
      id, device_uuid, platform, app_version, tier, token_version, is_blocked, metadata
    ) VALUES (
      ${id}, ${deviceUuid}, ${platform}, ${appVersion}, 'free', 1, false, '{}'::jsonb
    )
    RETURNING *
  `;

  const newDevice = mapRowToDevice(result.rows[0]);

  logger.info('New device registered', {
    device_id: id,
    device_uuid: deviceUuid,
    platform,
  });

  return { device: newDevice, isNew: true };
}

/**
 * Find device by UUID
 */
export async function findDeviceByUuid(deviceUuid: string): Promise<Device | null> {
  const result = await sql`
    SELECT * FROM devices WHERE device_uuid = ${deviceUuid}
  `;

  if (result.rows.length === 0) {
    return null;
  }

  return mapRowToDevice(result.rows[0]);
}

/**
 * Get device by ID
 */
export async function getDeviceById(id: string): Promise<Device | null> {
  const result = await sql`
    SELECT * FROM devices WHERE id = ${id}
  `;

  if (result.rows.length === 0) {
    return null;
  }

  return mapRowToDevice(result.rows[0]);
}

/**
 * Update device token version (for token revocation)
 */
export async function incrementTokenVersion(deviceUuid: string): Promise<number> {
  const result = await sql`
    UPDATE devices
    SET token_version = token_version + 1
    WHERE device_uuid = ${deviceUuid}
    RETURNING token_version
  `;

  if (result.rows.length === 0) {
    throw new Error('Device not found');
  }

  return result.rows[0].token_version as number;
}

/**
 * Update device tier (for subscription changes)
 */
export async function updateDeviceTier(
  deviceUuid: string,
  tier: 'free' | 'premium',
  subscriptionId: string | null
): Promise<Device> {
  const result = await sql`
    UPDATE devices
    SET tier = ${tier},
        subscription_id = ${subscriptionId},
        token_version = token_version + 1
    WHERE device_uuid = ${deviceUuid}
    RETURNING *
  `;

  if (result.rows.length === 0) {
    throw new Error('Device not found');
  }

  return mapRowToDevice(result.rows[0]);
}

/**
 * Block a device
 */
export async function blockDevice(deviceUuid: string, reason: string): Promise<void> {
  await sql`
    UPDATE devices
    SET is_blocked = true,
        block_reason = ${reason},
        token_version = token_version + 1
    WHERE device_uuid = ${deviceUuid}
  `;

  logger.warn('Device blocked', {
    device_uuid: deviceUuid,
    reason,
  });
}

/**
 * Delete device (for GDPR compliance)
 */
export async function deleteDevice(deviceUuid: string): Promise<boolean> {
  const result = await sql`
    DELETE FROM devices WHERE device_uuid = ${deviceUuid}
  `;

  const deleted = (result.rowCount ?? 0) > 0;

  if (deleted) {
    logger.info('Device deleted (GDPR)', {
      device_uuid: deviceUuid,
    });
  }

  return deleted;
}

// -----------------------------------------------------------------------------
// Token Refresh
// -----------------------------------------------------------------------------

/**
 * Refresh tokens for a device
 */
export async function refreshTokens(refreshToken: string): Promise<TokenResponse> {
  // Verify refresh token
  const { sub: deviceUuid, version } = verifyRefreshToken(refreshToken);

  // Get device
  const device = await findDeviceByUuid(deviceUuid);

  if (!device) {
    throw new TokenError('INVALID_TOKEN', 'Device not found');
  }

  if (device.is_blocked) {
    throw new TokenError('DEVICE_BLOCKED', 'Device has been blocked');
  }

  // Check token version (for revocation)
  if (device.token_version !== version) {
    throw new TokenError('TOKEN_REVOKED', 'Token has been revoked');
  }

  // Generate new tokens
  return generateTokenPair(device);
}

// -----------------------------------------------------------------------------
// Helper Functions
// -----------------------------------------------------------------------------

/**
 * Map database row to Device type
 */
function mapRowToDevice(row: Record<string, unknown>): Device {
  return {
    id: row.id as string,
    device_uuid: row.device_uuid as string,
    platform: row.platform as 'ios' | 'android',
    app_version: row.app_version as string,
    created_at: new Date(row.created_at as string),
    last_seen_at: new Date(row.last_seen_at as string),
    refresh_token: row.refresh_token as string | null,
    token_version: row.token_version as number,
    tier: row.tier as 'free' | 'premium',
    subscription_id: row.subscription_id as string | null,
    is_blocked: row.is_blocked as boolean,
    block_reason: row.block_reason as string | null,
    metadata: row.metadata as Record<string, unknown>,
  };
}

// -----------------------------------------------------------------------------
// Custom Error Class
// -----------------------------------------------------------------------------

export class TokenError extends Error {
  code: 'TOKEN_EXPIRED' | 'INVALID_TOKEN' | 'TOKEN_REVOKED' | 'DEVICE_BLOCKED';

  constructor(
    code: 'TOKEN_EXPIRED' | 'INVALID_TOKEN' | 'TOKEN_REVOKED' | 'DEVICE_BLOCKED',
    message: string
  ) {
    super(message);
    this.code = code;
    this.name = 'TokenError';
  }
}
