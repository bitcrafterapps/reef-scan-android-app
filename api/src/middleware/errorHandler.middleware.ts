// ============================================================================
// Error Handler Middleware
// Centralized error handling with consistent response format
// ============================================================================

import { Request, Response, NextFunction } from 'express';
import config from '../config';
import type { ApiError, ErrorCode } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Custom Error Classes
// -----------------------------------------------------------------------------

export class AppError extends Error {
  code: ErrorCode;
  statusCode: number;
  details?: Record<string, unknown>;
  retryAfter?: number;

  constructor(
    code: ErrorCode,
    message: string,
    statusCode = 500,
    details?: Record<string, unknown>,
    retryAfter?: number
  ) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
    this.details = details;
    this.retryAfter = retryAfter;
    this.name = 'AppError';
  }
}

export class ValidationError extends AppError {
  constructor(message: string, details?: Record<string, unknown>) {
    super('INVALID_REQUEST', message, 400, details);
    this.name = 'ValidationError';
  }
}

export class AuthenticationError extends AppError {
  constructor(message: string) {
    super('UNAUTHORIZED', message, 401);
    this.name = 'AuthenticationError';
  }
}

export class AuthorizationError extends AppError {
  constructor(message: string) {
    super('DEVICE_BLOCKED', message, 403);
    this.name = 'AuthorizationError';
  }
}

export class NotFoundError extends AppError {
  constructor(message: string) {
    super('INVALID_REQUEST', message, 404);
    this.name = 'NotFoundError';
  }
}

export class RateLimitError extends AppError {
  constructor(message: string, retryAfter: number, details?: Record<string, unknown>) {
    super('RATE_LIMIT_EXCEEDED', message, 429, details, retryAfter);
    this.name = 'RateLimitError';
  }
}

export class ServiceUnavailableError extends AppError {
  constructor(message: string, retryAfter?: number) {
    super('AI_UNAVAILABLE', message, 503, undefined, retryAfter);
    this.name = 'ServiceUnavailableError';
  }
}

// -----------------------------------------------------------------------------
// Error Code to HTTP Status Mapping
// -----------------------------------------------------------------------------

// Error code to status mapping (exported for potential use)
export const ERROR_STATUS_MAP: Record<ErrorCode, number> = {
  RATE_LIMIT_EXCEEDED: 429,
  INVALID_TOKEN: 401,
  INVALID_IMAGE: 400,
  AI_UNAVAILABLE: 503,
  INTERNAL_ERROR: 500,
  DEVICE_BLOCKED: 403,
  INVALID_REQUEST: 400,
  UNAUTHORIZED: 401,
};

// -----------------------------------------------------------------------------
// Error Handler Middleware
// -----------------------------------------------------------------------------

/**
 * Global error handler middleware
 */
export function errorHandler(
  err: Error,
  req: Request,
  res: Response,
  _next: NextFunction
): void {
  // Log the error
  logger.error('Request error', {
    request_id: req.requestId,
    error: err.message,
    stack: config.isDevelopment ? err.stack : undefined,
    path: req.path,
    method: req.method,
  });

  // Handle AppError instances
  if (err instanceof AppError) {
    const response: ApiError = {
      error: {
        code: err.code,
        message: err.message,
        details: err.details,
        retry_after: err.retryAfter,
      },
    };

    res.status(err.statusCode).json(response);
    return;
  }

  // Handle validation errors from Zod or similar
  if (err.name === 'ZodError') {
    const response: ApiError = {
      error: {
        code: 'INVALID_REQUEST',
        message: 'Validation failed',
        details: { errors: (err as Error & { errors?: unknown[] }).errors },
      },
    };

    res.status(400).json(response);
    return;
  }

  // Handle JWT errors
  if (err.name === 'JsonWebTokenError' || err.name === 'TokenExpiredError') {
    const response: ApiError = {
      error: {
        code: 'INVALID_TOKEN',
        message: err.message,
      },
    };

    res.status(401).json(response);
    return;
  }

  // Handle unknown errors
  const response: ApiError = {
    error: {
      code: 'INTERNAL_ERROR',
      message: config.isProduction
        ? 'An unexpected error occurred'
        : err.message,
    },
  };

  res.status(500).json(response);
}

// -----------------------------------------------------------------------------
// Async Handler Wrapper
// -----------------------------------------------------------------------------

/**
 * Wrap async route handlers to catch errors
 */
export function asyncHandler(
  fn: (req: Request, res: Response, next: NextFunction) => Promise<void>
): (req: Request, res: Response, next: NextFunction) => void {
  return (req: Request, res: Response, next: NextFunction): void => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

// -----------------------------------------------------------------------------
// Not Found Handler
// -----------------------------------------------------------------------------

/**
 * 404 handler for undefined routes
 */
export function notFoundHandler(req: Request, res: Response): void {
  const response: ApiError = {
    error: {
      code: 'INVALID_REQUEST',
      message: `Endpoint not found: ${req.method} ${req.path}`,
    },
  };

  res.status(404).json(response);
}

// -----------------------------------------------------------------------------
// Request Timeout Handler
// -----------------------------------------------------------------------------

/**
 * Create timeout middleware
 */
export function timeoutHandler(timeoutMs: number) {
  return (_req: Request, res: Response, next: NextFunction): void => {
    const timeout = setTimeout(() => {
      if (!res.headersSent) {
        const response: ApiError = {
          error: {
            code: 'INTERNAL_ERROR',
            message: 'Request timeout',
            retry_after: 5,
          },
        };

        res.status(504).json(response);
      }
    }, timeoutMs);

    res.on('finish', () => {
      clearTimeout(timeout);
    });

    next();
  };
}
