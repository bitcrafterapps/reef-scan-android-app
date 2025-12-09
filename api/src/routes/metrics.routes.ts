// ============================================================================
// Metrics Routes
// Dashboard metrics endpoints
// ============================================================================

import { Router, Request, Response } from 'express';
import { getDashboardMetrics } from '../services/metrics.service';
import logger from '../utils/logger';

const router = Router();

// -----------------------------------------------------------------------------
// GET /v1/metrics
// Get dashboard metrics (public endpoint for dashboard)
// -----------------------------------------------------------------------------

router.get('/', async (req: Request, res: Response): Promise<void> => {
  try {
    const metrics = await getDashboardMetrics();
    res.json(metrics);
  } catch (error) {
    logger.error('Failed to get metrics', {
      request_id: req.requestId,
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    res.status(500).json({
      error: {
        code: 'INTERNAL_ERROR',
        message: 'Failed to retrieve metrics',
      },
    });
  }
});

export default router;
