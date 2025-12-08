// ============================================================================
// Rate Limit Middleware
// Enforces rate limits and adds rate limit headers to responses
// ============================================================================

import { Request, Response, NextFunction } from 'express';
import {
  checkRateLimit,
  checkIpRateLimit,
  getRateLimitInfo,
  formatResetTime,
} from '../services/rateLimit.service';
import type { ApiError, RateLimitResult, RateLimitHeaders } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Rate Limit Enforcement Middleware
// -----------------------------------------------------------------------------

/**
 * Middleware that enforces rate limits for authenticated requests
 * Must be used after auth middleware (requires req.device)
 */
export async function enforceRateLimit(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  try {
    // Require authenticated device
    if (!req.device) {
      const error: ApiError = {
        error: {
          code: 'UNAUTHORIZED',
          message: 'Authentication required for rate-limited endpoints',
        },
      };
      res.status(401).json(error);
      return;
    }

    const { device_uuid, tier } = req.device;

    // Check rate limit
    const result = await checkRateLimit(device_uuid, tier);

    // Always add rate limit headers
    addRateLimitHeaders(res, result);

    if (!result.allowed) {
      logger.rateLimit(device_uuid, tier, result.limit, result.remaining, true);

      const error: ApiError = {
        error: {
          code: 'RATE_LIMIT_EXCEEDED',
          message: `Daily limit of ${result.limit} requests exceeded`,
          details: {
            limit: result.limit,
            remaining: result.remaining,
            reset_at: formatResetTime(result.reset_at),
            tier: result.tier,
          },
          retry_after: result.reset_at - Math.floor(Date.now() / 1000),
        },
      };

      // Add upgrade URL for free tier
      if (result.upgrade_url) {
        (error.error.details as Record<string, unknown>).upgrade_url = result.upgrade_url;
      }

      res.status(429).json(error);
      return;
    }

    // Store rate limit result on request for later use
    (req as RequestWithRateLimit).rateLimit = result;

    next();
  } catch (error) {
    logger.error('Rate limit check failed', {
      request_id: req.requestId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    // Fail open - allow request if rate limit check fails
    // This prevents Redis issues from blocking all requests
    next();
  }
}

/**
 * Middleware that enforces IP-based rate limits
 * Should be used before authentication for abuse prevention
 */
export async function enforceIpRateLimit(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  try {
    // Get client IP (handle proxies)
    const ip = getClientIp(req);

    if (!ip) {
      // Can't determine IP, allow request
      next();
      return;
    }

    const allowed = await checkIpRateLimit(ip);

    if (!allowed) {
      logger.warn('IP rate limit exceeded', {
        ip,
        request_id: req.requestId,
      });

      const error: ApiError = {
        error: {
          code: 'RATE_LIMIT_EXCEEDED',
          message: 'Too many requests from this IP address',
          retry_after: 3600, // 1 hour
        },
      };

      res.status(429).json(error);
      return;
    }

    next();
  } catch (error) {
    logger.error('IP rate limit check failed', {
      request_id: req.requestId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    // Fail open
    next();
  }
}

// -----------------------------------------------------------------------------
// Rate Limit Headers Middleware
// -----------------------------------------------------------------------------

/**
 * Middleware that adds rate limit headers to responses
 * Can be used independently to show rate limit info without enforcing
 */
export async function addRateLimitInfo(
  req: Request,
  res: Response,
  next: NextFunction
): Promise<void> {
  // Only add headers if device is authenticated
  if (req.device) {
    const result = await getRateLimitInfo(req.device.device_uuid, req.device.tier);
    addRateLimitHeaders(res, result);
  }

  next();
}

/**
 * Add rate limit headers to response
 */
function addRateLimitHeaders(res: Response, result: RateLimitResult): void {
  const headers: RateLimitHeaders = {
    'X-RateLimit-Limit': String(result.limit),
    'X-RateLimit-Remaining': String(result.remaining),
    'X-RateLimit-Reset': String(result.reset_at),
    'X-RateLimit-Window': 'day',
    'X-RateLimit-Tier': result.tier,
  };

  Object.entries(headers).forEach(([key, value]) => {
    res.setHeader(key, value);
  });
}

// -----------------------------------------------------------------------------
// Helper Functions
// -----------------------------------------------------------------------------

/**
 * Get client IP address, handling proxies
 */
function getClientIp(req: Request): string | null {
  // Check X-Forwarded-For header (for proxies/load balancers)
  const forwardedFor = req.headers['x-forwarded-for'];
  if (forwardedFor) {
    const ips = typeof forwardedFor === 'string'
      ? forwardedFor.split(',')
      : forwardedFor;
    const clientIp = ips[0]?.trim();
    if (clientIp) {
      return clientIp;
    }
  }

  // Check X-Real-IP header
  const realIp = req.headers['x-real-ip'];
  if (typeof realIp === 'string') {
    return realIp;
  }

  // Fall back to connection remote address
  return req.ip || req.socket?.remoteAddress || null;
}

// -----------------------------------------------------------------------------
// Type Extensions
// -----------------------------------------------------------------------------

interface RequestWithRateLimit extends Request {
  rateLimit?: RateLimitResult;
}

// Extend Express Request type
declare global {
  namespace Express {
    interface Request {
      rateLimit?: RateLimitResult;
    }
  }
}
