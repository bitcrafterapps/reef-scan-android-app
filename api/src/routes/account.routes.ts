// ============================================================================
// Account Routes
// GDPR compliance: data export and deletion
// ============================================================================

import { Router, Request, Response } from 'express';
import { sql } from '../db';
import { requireAuth } from '../middleware/auth.middleware';
import { deleteDevice } from '../services/auth.service';
import type { ApiError } from '../types';
import logger from '../utils/logger';

const router = Router();

// -----------------------------------------------------------------------------
// DELETE /v1/account
// Delete all device data (GDPR right to erasure)
// -----------------------------------------------------------------------------

router.delete(
  '/',
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

      const deviceUuid = req.device.device_uuid;
      const deviceId = req.device.id;

      // Delete all associated data
      await Promise.all([
        // Delete request logs
        sql`DELETE FROM request_logs WHERE device_id = ${deviceId}`,
        // Delete daily usage
        sql`DELETE FROM daily_usage WHERE device_id = ${deviceId}`,
      ]);

      // Delete the device record
      await deleteDevice(deviceUuid);

      logger.info('Account deleted (GDPR)', {
        device_uuid: deviceUuid,
        request_id: req.requestId,
      });

      res.json({
        success: true,
        message: 'All account data has been deleted',
      });
    } catch (error) {
      logger.error('Account deletion failed', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to delete account data',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// GET /v1/account/export
// Export all device data (GDPR right to data portability)
// -----------------------------------------------------------------------------

router.get(
  '/export',
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

      const deviceId = req.device.id;

      // Get all device data
      const [deviceData, usageData, requestLogs] = await Promise.all([
        // Device info (excluding sensitive fields)
        sql`
          SELECT device_uuid, platform, app_version, created_at, last_seen_at,
                 tier, subscription_id
          FROM devices
          WHERE id = ${deviceId}
        `,
        // Usage data
        sql`
          SELECT date, request_count, tokens_used
          FROM daily_usage
          WHERE device_id = ${deviceId}
          ORDER BY date DESC
          LIMIT 365
        `,
        // Request logs (last 30 days, anonymized)
        sql`
          SELECT mode, provider_used, status, latency_ms, created_at
          FROM request_logs
          WHERE device_id = ${deviceId}
            AND created_at > NOW() - INTERVAL '30 days'
          ORDER BY created_at DESC
        `,
      ]);

      const exportData = {
        export_date: new Date().toISOString(),
        device: deviceData.rows[0] || null,
        usage_history: usageData.rows,
        request_history: requestLogs.rows,
        data_retention_policy: {
          request_logs: '30 days',
          usage_data: '365 days',
          images: 'Not stored (processed in memory only)',
        },
      };

      logger.info('Account data exported (GDPR)', {
        device_uuid: req.device.device_uuid,
        request_id: req.requestId,
      });

      res.json(exportData);
    } catch (error) {
      logger.error('Account export failed', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to export account data',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// GET /v1/account
// Get account information
// -----------------------------------------------------------------------------

router.get(
  '/',
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

      res.json({
        device_uuid: req.device.device_uuid,
        platform: req.device.platform,
        app_version: req.device.app_version,
        tier: req.device.tier,
        subscription_id: req.device.subscription_id,
        created_at: req.device.created_at,
        last_seen_at: req.device.last_seen_at,
      });
    } catch (error) {
      logger.error('Get account failed', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to get account information',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

export default router;
