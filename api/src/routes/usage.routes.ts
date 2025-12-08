// ============================================================================
// Usage Routes
// Endpoints for checking usage and rate limit status
// ============================================================================

import { Router, Request, Response } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { addRateLimitInfo } from '../middleware/rateLimit.middleware';
import { getUsage, getUsageStats } from '../services/usage.service';
import type { ApiError } from '../types';
import logger from '../utils/logger';

const router = Router();

// -----------------------------------------------------------------------------
// GET /v1/usage
// Get current usage information for the authenticated device
// -----------------------------------------------------------------------------

router.get(
  '/',
  requireAuth,
  addRateLimitInfo,
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

      const usage = await getUsage(req.device.device_uuid, req.device.tier);

      res.json(usage);
    } catch (error) {
      logger.error('Failed to get usage', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to retrieve usage information',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// GET /v1/usage/stats
// Get detailed usage statistics for the authenticated device
// -----------------------------------------------------------------------------

router.get(
  '/stats',
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

      // Parse date range from query params (default to last 30 days)
      const endDate = new Date();
      const startDate = new Date();
      startDate.setDate(startDate.getDate() - 30);

      if (req.query.start_date) {
        const parsedStart = new Date(req.query.start_date as string);
        if (!isNaN(parsedStart.getTime())) {
          startDate.setTime(parsedStart.getTime());
        }
      }

      if (req.query.end_date) {
        const parsedEnd = new Date(req.query.end_date as string);
        if (!isNaN(parsedEnd.getTime())) {
          endDate.setTime(parsedEnd.getTime());
        }
      }

      const stats = await getUsageStats(req.device.id, startDate, endDate);

      res.json({
        period: {
          start: startDate.toISOString(),
          end: endDate.toISOString(),
        },
        ...stats,
      });
    } catch (error) {
      logger.error('Failed to get usage stats', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to retrieve usage statistics',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

export default router;
