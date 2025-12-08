// ============================================================================
// App Secret Middleware
// Validates that requests come from legitimate app installations
// ============================================================================

import { Request, Response, NextFunction } from 'express';
import config from '../config';
import type { ApiError } from '../types';
import logger from '../utils/logger';

/**
 * Supported platforms
 */
type Platform = 'ios' | 'android';

/**
 * Validate app secret for device registration
 * The secret should be passed in the request body along with platform
 */
export function validateAppSecret(
  req: Request,
  res: Response,
  next: NextFunction
): void {
  const { app_secret, platform } = req.body as { app_secret?: string; platform?: string };

  if (!app_secret) {
    sendInvalidSecret(res, 'App secret is required');
    return;
  }

  if (!platform || !isValidPlatform(platform)) {
    sendInvalidSecret(res, 'Valid platform (ios or android) is required');
    return;
  }

  // Validate secret based on platform
  const isValid = validateSecretForPlatform(app_secret, platform as Platform);

  if (!isValid) {
    // Log invalid secret attempt
    logger.warn('Invalid app secret attempt', {
      platform,
      request_id: req.requestId,
      ip: req.ip,
      user_agent: req.headers['user-agent'],
    });

    sendInvalidSecret(res, 'Invalid app secret');
    return;
  }

  next();
}

/**
 * Validate secret matches configured secret for platform
 * Supports versioned secrets for rotation
 */
function validateSecretForPlatform(secret: string, platform: Platform): boolean {
  const platformSecrets = config.appSecrets[platform];

  if (!platformSecrets) {
    return false;
  }

  // Check against all versions (for secret rotation support)
  const validSecrets = Object.values(platformSecrets).filter(Boolean);

  if (validSecrets.length === 0) {
    // In development, allow if no secrets configured
    if (config.isDevelopment) {
      logger.warn('No app secrets configured - allowing request in development');
      return true;
    }
    return false;
  }

  return validSecrets.includes(secret);
}

/**
 * Check if platform string is valid
 */
function isValidPlatform(platform: string): boolean {
  return platform === 'ios' || platform === 'android';
}

/**
 * Send invalid secret error response
 */
function sendInvalidSecret(res: Response, message: string): void {
  const error: ApiError = {
    error: {
      code: 'UNAUTHORIZED',
      message,
    },
  };
  res.status(401).json(error);
}

/**
 * Extract app secret from various locations
 * Supports both header and body for flexibility
 */
export function extractAppSecret(req: Request): string | null {
  // First check body (for registration)
  if (req.body?.app_secret) {
    return req.body.app_secret as string;
  }

  // Then check header
  const headerSecret = req.headers['x-app-secret'];
  if (typeof headerSecret === 'string') {
    return headerSecret;
  }

  return null;
}

/**
 * Get platform from various locations
 */
export function extractPlatform(req: Request): Platform | null {
  // First check body
  if (req.body?.platform) {
    const platform = req.body.platform as string;
    if (isValidPlatform(platform)) {
      return platform as Platform;
    }
  }

  // Then check header
  const headerPlatform = req.headers['x-platform'];
  if (typeof headerPlatform === 'string' && isValidPlatform(headerPlatform)) {
    return headerPlatform as Platform;
  }

  return null;
}
