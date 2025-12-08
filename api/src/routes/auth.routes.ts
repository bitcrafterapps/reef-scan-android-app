// ============================================================================
// Authentication Routes
// Device registration and token management
// ============================================================================

import { Router, Request, Response } from 'express';
import {
  registerDevice,
  generateTokenPair,
  refreshTokens,
  TokenError,
} from '../services/auth.service';
import { validateAppSecret } from '../middleware/appSecret.middleware';
import {
  validate,
  RegisterRequestSchema,
  RefreshTokenRequestSchema,
} from '../utils/validation';
import type { ApiError, TokenResponse } from '../types';
import logger from '../utils/logger';

const router = Router();

// -----------------------------------------------------------------------------
// POST /v1/auth/register
// Register a new device or get tokens for existing device
// -----------------------------------------------------------------------------

router.post(
  '/register',
  validate(RegisterRequestSchema),
  validateAppSecret,
  async (req: Request, res: Response): Promise<void> => {
    try {
      const { device_uuid, platform, app_version } = req.body as {
        device_uuid: string;
        platform: 'ios' | 'android';
        app_version: string;
      };

      // Register device (creates new or returns existing)
      const { device, isNew } = await registerDevice(
        device_uuid,
        platform,
        app_version
      );

      // Generate tokens
      const tokens: TokenResponse = generateTokenPair(device);

      logger.info('Device registration successful', {
        request_id: req.requestId,
        device_uuid,
        platform,
        is_new: isNew,
        tier: device.tier,
      });

      // Return tokens with additional device info
      res.status(isNew ? 201 : 200).json({
        ...tokens,
        device: {
          tier: device.tier,
          daily_limit:
            device.tier === 'premium'
              ? 20 // config.rateLimit.premium.daily
              : 3, // config.rateLimit.free.daily
          subscription_status: device.subscription_id ? 'active' : 'none',
        },
      });
    } catch (error) {
      logger.error('Device registration failed', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to register device',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// POST /v1/auth/refresh
// Refresh access token using refresh token
// -----------------------------------------------------------------------------

router.post(
  '/refresh',
  validate(RefreshTokenRequestSchema),
  async (req: Request, res: Response): Promise<void> => {
    try {
      const { refresh_token } = req.body as { refresh_token: string };

      // Refresh tokens
      const tokens = await refreshTokens(refresh_token);

      logger.info('Token refresh successful', {
        request_id: req.requestId,
      });

      res.json(tokens);
    } catch (error) {
      if (error instanceof TokenError) {
        const statusCode = getTokenErrorStatus(error);
        const apiError: ApiError = {
          error: {
            code: error.code === 'DEVICE_BLOCKED' ? 'DEVICE_BLOCKED' : 'INVALID_TOKEN',
            message: error.message,
          },
        };

        logger.warn('Token refresh failed', {
          request_id: req.requestId,
          error_code: error.code,
        });

        res.status(statusCode).json(apiError);
        return;
      }

      logger.error('Token refresh error', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to refresh token',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// POST /v1/auth/revoke
// Revoke all tokens for a device (logout)
// Requires authentication
// -----------------------------------------------------------------------------

import { requireAuth } from '../middleware/auth.middleware';
import { incrementTokenVersion } from '../services/auth.service';

router.post(
  '/revoke',
  requireAuth,
  async (req: Request, res: Response): Promise<void> => {
    try {
      if (!req.device) {
        const error: ApiError = {
          error: {
            code: 'UNAUTHORIZED',
            message: 'Device not found',
          },
        };
        res.status(401).json(error);
        return;
      }

      // Increment token version to invalidate all existing tokens
      await incrementTokenVersion(req.device.device_uuid);

      logger.info('Tokens revoked', {
        request_id: req.requestId,
        device_uuid: req.device.device_uuid,
      });

      res.json({ success: true, message: 'All tokens have been revoked' });
    } catch (error) {
      logger.error('Token revocation failed', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to revoke tokens',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// GET /v1/auth/me
// Get current device info
// Requires authentication
// -----------------------------------------------------------------------------

router.get(
  '/me',
  requireAuth,
  async (req: Request, res: Response): Promise<void> => {
    try {
      if (!req.device || !req.jwt) {
        const error: ApiError = {
          error: {
            code: 'UNAUTHORIZED',
            message: 'Device not found',
          },
        };
        res.status(401).json(error);
        return;
      }

      res.json({
        device_uuid: req.device.device_uuid,
        platform: req.device.platform,
        tier: req.device.tier,
        daily_limit: req.jwt.daily_limit,
        subscription_id: req.device.subscription_id,
        created_at: req.device.created_at,
        last_seen_at: req.device.last_seen_at,
      });
    } catch (error) {
      logger.error('Get device info failed', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to get device info',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// Helper Functions
// -----------------------------------------------------------------------------

function getTokenErrorStatus(error: TokenError): number {
  switch (error.code) {
    case 'TOKEN_EXPIRED':
    case 'INVALID_TOKEN':
    case 'TOKEN_REVOKED':
      return 401;
    case 'DEVICE_BLOCKED':
      return 403;
    default:
      return 401;
  }
}

export default router;
