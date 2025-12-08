// ============================================================================
// Request Validation Schemas
// Using Zod for runtime type validation
// ============================================================================

import { z } from 'zod';
import { Request, Response, NextFunction } from 'express';
import type { ApiError } from '../types';

// -----------------------------------------------------------------------------
// Auth Schemas
// -----------------------------------------------------------------------------

export const RegisterRequestSchema = z.object({
  device_uuid: z
    .string()
    .uuid('device_uuid must be a valid UUID')
    .min(1, 'device_uuid is required'),
  platform: z.enum(['ios', 'android'], {
    errorMap: () => ({ message: 'platform must be ios or android' }),
  }),
  app_version: z
    .string()
    .min(1, 'app_version is required')
    .regex(/^\d+\.\d+\.\d+$/, 'app_version must be in format x.y.z'),
  app_secret: z.string().min(1, 'app_secret is required'),
});

export const RefreshTokenRequestSchema = z.object({
  refresh_token: z.string().min(1, 'refresh_token is required'),
});

// -----------------------------------------------------------------------------
// Analysis Schemas
// -----------------------------------------------------------------------------

export const AnalyzeRequestSchema = z.object({
  image: z.object({
    data: z
      .string()
      .min(1, 'image.data is required')
      .refine(
        (val) => {
          // Check if it's valid base64
          try {
            // Check base64 length (rough check for 5MB limit)
            const estimatedBytes = (val.length * 3) / 4;
            return estimatedBytes <= 5 * 1024 * 1024; // 5MB
          } catch {
            return false;
          }
        },
        { message: 'image.data must be valid base64 and under 5MB' }
      ),
    mime_type: z.enum(['image/jpeg', 'image/png'], {
      errorMap: () => ({
        message: 'image.mime_type must be image/jpeg or image/png',
      }),
    }),
  }),
  mode: z.enum(['comprehensive', 'fish_id', 'coral_id', 'algae_id', 'pest_id'], {
    errorMap: () => ({
      message:
        'mode must be one of: comprehensive, fish_id, coral_id, algae_id, pest_id',
    }),
  }),
  options: z
    .object({
      include_recommendations: z.boolean().optional(),
      language: z.string().min(2).max(5).optional(),
    })
    .optional(),
});

// -----------------------------------------------------------------------------
// Type Exports
// -----------------------------------------------------------------------------

export type RegisterRequest = z.infer<typeof RegisterRequestSchema>;
export type RefreshTokenRequest = z.infer<typeof RefreshTokenRequestSchema>;
export type AnalyzeRequest = z.infer<typeof AnalyzeRequestSchema>;

// -----------------------------------------------------------------------------
// Validation Middleware Factory
// -----------------------------------------------------------------------------

/**
 * Create validation middleware for a Zod schema
 */
export function validate<T extends z.ZodSchema>(schema: T) {
  return (req: Request, res: Response, next: NextFunction): void => {
    const result = schema.safeParse(req.body);

    if (!result.success) {
      const errors = result.error.errors.map((err) => ({
        field: err.path.join('.'),
        message: err.message,
      }));

      const error: ApiError = {
        error: {
          code: 'INVALID_REQUEST',
          message: 'Validation failed',
          details: { errors },
        },
      };

      res.status(400).json(error);
      return;
    }

    // Replace body with parsed/typed data
    req.body = result.data;
    next();
  };
}

// -----------------------------------------------------------------------------
// Common Validators
// -----------------------------------------------------------------------------

/**
 * Validate UUID format
 */
export function isValidUuid(value: string): boolean {
  const uuidRegex =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  return uuidRegex.test(value);
}

/**
 * Validate base64 string
 */
export function isValidBase64(value: string): boolean {
  try {
    // Check if it looks like base64
    const base64Regex = /^[A-Za-z0-9+/]+=*$/;
    return base64Regex.test(value.replace(/\s/g, ''));
  } catch {
    return false;
  }
}

/**
 * Estimate base64 data size in bytes
 */
export function estimateBase64Size(base64: string): number {
  // Remove data URL prefix if present
  const data = base64.replace(/^data:image\/\w+;base64,/, '');
  // Estimate byte size: (length * 3) / 4
  const padding = (data.match(/=/g) || []).length;
  return (data.length * 3) / 4 - padding;
}

/**
 * Sanitize string input (remove potential XSS)
 */
export function sanitizeString(value: string): string {
  return value
    .replace(/[<>]/g, '') // Remove angle brackets
    .replace(/javascript:/gi, '') // Remove javascript: protocol
    .trim();
}
