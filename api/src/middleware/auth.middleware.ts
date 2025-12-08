// ============================================================================
// Authentication Middleware
// JWT verification and device loading
// ============================================================================

import { Request, Response, NextFunction } from 'express';
import { verifyAccessToken, findDeviceByUuid, TokenError } from '../services/auth.service';
import type { ApiError } from '../types';
import logger from '../utils/logger';

/**
 * Middleware to verify JWT token and attach device to request
 */
export async function requireAuth(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  try {
    // Extract token from Authorization header
    const authHeader = req.headers.authorization;

    if (!authHeader) {
      sendUnauthorized(res, 'UNAUTHORIZED', 'Authorization header required');
      return;
    }

    if (!authHeader.startsWith('Bearer ')) {
      sendUnauthorized(res, 'UNAUTHORIZED', 'Invalid authorization format. Use: Bearer <token>');
      return;
    }

    const token = authHeader.slice(7); // Remove 'Bearer ' prefix

    if (!token) {
      sendUnauthorized(res, 'UNAUTHORIZED', 'Token required');
      return;
    }

    // Verify token
    const payload = verifyAccessToken(token);

    // Load device from database
    const device = await findDeviceByUuid(payload.sub);

    if (!device) {
      sendUnauthorized(res, 'INVALID_TOKEN', 'Device not found');
      return;
    }

    // Check if device is blocked
    if (device.is_blocked) {
      const error: ApiError = {
        error: {
          code: 'DEVICE_BLOCKED',
          message: device.block_reason || 'Device has been blocked',
        },
      };
      res.status(403).json(error);
      return;
    }

    // Attach JWT payload and device to request
    req.jwt = payload;
    req.device = device;

    next();
  } catch (error) {
    if (error instanceof TokenError) {
      handleTokenError(res, error);
      return;
    }

    logger.error('Auth middleware error', {
      error: error instanceof Error ? error.message : 'Unknown error',
      request_id: req.requestId,
    });

    const apiError: ApiError = {
      error: {
        code: 'INTERNAL_ERROR',
        message: 'Authentication failed',
      },
    };
    res.status(500).json(apiError);
  }
}

/**
 * Optional auth middleware - attaches device if token present, but doesn't require it
 */
export async function optionalAuth(
  req: Request,
  _res: Response,
  next: NextFunction
): Promise<void> {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      next();
      return;
    }

    const token = authHeader.slice(7);

    if (!token) {
      next();
      return;
    }

    // Try to verify token
    const payload = verifyAccessToken(token);
    const device = await findDeviceByUuid(payload.sub);

    if (device && !device.is_blocked) {
      req.jwt = payload;
      req.device = device;
    }

    next();
  } catch {
    // Ignore token errors for optional auth
    next();
  }
}

// -----------------------------------------------------------------------------
// Helper Functions
// -----------------------------------------------------------------------------

function sendUnauthorized(
  res: Response,
  code: 'UNAUTHORIZED' | 'INVALID_TOKEN',
  message: string
): void {
  const error: ApiError = {
    error: {
      code,
      message,
    },
  };
  res.status(401).json(error);
}

function handleTokenError(res: Response, error: TokenError): void {
  const apiError: ApiError = {
    error: {
      code: error.code === 'DEVICE_BLOCKED' ? 'DEVICE_BLOCKED' : 'INVALID_TOKEN',
      message: error.message,
    },
  };

  const status = error.code === 'DEVICE_BLOCKED' ? 403 : 401;
  res.status(status).json(apiError);
}
