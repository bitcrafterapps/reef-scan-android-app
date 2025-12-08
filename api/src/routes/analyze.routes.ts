// ============================================================================
// Analyze Routes
// Main image analysis endpoint
// ============================================================================

import { Router, Request, Response } from 'express';
import { requireAuth } from '../middleware/auth.middleware';
import { enforceRateLimit } from '../middleware/rateLimit.middleware';
import { validate, AnalyzeRequestSchema } from '../utils/validation';
import * as aiProvider from '../services/aiProvider.service';
import * as cache from '../services/cache.service';
import * as usage from '../services/usage.service';
import type { ApiError, AnalysisMode, ScanResult } from '../types';
import logger from '../utils/logger';

const router = Router();

// -----------------------------------------------------------------------------
// POST /v1/analyze
// Analyze a reef tank image
// -----------------------------------------------------------------------------

router.post(
  '/',
  requireAuth,
  enforceRateLimit,
  validate(AnalyzeRequestSchema),
  async (req: Request, res: Response): Promise<void> => {
    const startTime = Date.now();

    try {
      if (!req.device || !req.requestId) {
        const error: ApiError = {
          error: {
            code: 'INTERNAL_ERROR',
            message: 'Request context missing',
          },
        };
        res.status(500).json(error);
        return;
      }

      const { image, mode, options } = req.body as {
        image: { data: string; mime_type: 'image/jpeg' | 'image/png' };
        mode: AnalysisMode;
        options?: { include_recommendations?: boolean; language?: string };
      };

      const requestId = req.requestId;
      const deviceId = req.device.device_uuid;

      // Check idempotency - return cached result for duplicate request
      const idempotentResult = await cache.getIdempotentResult(requestId);
      if (idempotentResult) {
        logger.info('Returning idempotent result', {
          request_id: requestId,
          device_id: deviceId,
        });

        res.json(idempotentResult);
        return;
      }

      // Calculate image hash for caching
      const imageHash = cache.calculateImageHash(image.data);

      // Check image cache
      const cachedResult = await cache.getCachedResult(imageHash, mode);
      if (cachedResult) {
        // Update usage info and return cached result
        const usageInfo = await usage.getUsageInfo(deviceId, req.device.tier);

        const result: ScanResult = {
          ...cachedResult,
          request_id: requestId,
          usage: usageInfo,
        };

        // Store for idempotency
        await cache.setIdempotentResult(requestId, result);

        logger.info('Returning cached result', {
          request_id: requestId,
          image_hash: imageHash.substring(0, 16),
          mode,
        });

        res.json(result);
        return;
      }

      // Check if any AI provider is available
      const providersAvailable = await aiProvider.isAnyProviderAvailable();
      if (!providersAvailable) {
        const error: ApiError = {
          error: {
            code: 'AI_UNAVAILABLE',
            message: 'AI analysis service is temporarily unavailable. Please try again later.',
            retry_after: 60,
          },
        };
        res.status(503).json(error);
        return;
      }

      // Perform analysis
      const analysisResult = await aiProvider.analyzeImage(
        image.data,
        image.mime_type,
        mode,
        requestId
      );

      if (!analysisResult.success || !analysisResult.result) {
        logger.error('Analysis failed', {
          request_id: requestId,
          provider: analysisResult.provider,
          error: analysisResult.error?.code,
        });

        const error: ApiError = {
          error: {
            code: 'AI_UNAVAILABLE',
            message: analysisResult.error?.message || 'Analysis failed',
            retry_after: 30,
          },
        };
        res.status(503).json(error);
        return;
      }

      // Get usage info
      const usageInfo = await usage.getUsageInfo(deviceId, req.device.tier);

      // Build final result
      const result: ScanResult = {
        ...analysisResult.result,
        request_id: requestId,
        usage: usageInfo,
      };

      // Filter recommendations if not requested
      if (options?.include_recommendations === false) {
        result.recommendations = [];
      }

      // Cache the result
      await Promise.all([
        cache.cacheResult(imageHash, mode, result),
        cache.setIdempotentResult(requestId, result),
      ]);

      // Record usage (async, don't wait)
      usage.recordUsage(
        req.device.id,
        mode,
        analysisResult.provider,
        analysisResult.apiKeyId || 'unknown',
        analysisResult.latencyMs,
        analysisResult.tokensUsed.input,
        analysisResult.tokensUsed.output,
        imageHash
      ).catch((err) => {
        logger.error('Failed to record usage', {
          error: err instanceof Error ? err.message : 'Unknown error',
        });
      });

      const totalLatency = Date.now() - startTime;

      logger.info('Analysis completed', {
        request_id: requestId,
        device_id: deviceId,
        mode,
        provider: analysisResult.provider,
        latency_ms: totalLatency,
        tank_health: result.tank_health,
        identifications_count: result.identifications.length,
      });

      res.json(result);
    } catch (error) {
      logger.error('Analyze endpoint error', {
        request_id: req.requestId,
        error: error instanceof Error ? error.message : 'Unknown error',
        stack: error instanceof Error ? error.stack : undefined,
      });

      const apiError: ApiError = {
        error: {
          code: 'INTERNAL_ERROR',
          message: 'An unexpected error occurred',
        },
      };
      res.status(500).json(apiError);
    }
  }
);

// -----------------------------------------------------------------------------
// GET /v1/analyze/status
// Get AI provider status
// -----------------------------------------------------------------------------

router.get(
  '/status',
  requireAuth,
  async (_req: Request, res: Response): Promise<void> => {
    try {
      const status = await aiProvider.getProviderStatus();

      res.json({
        available: status.gemini.available || status.openai.available,
        providers: {
          gemini: {
            status: status.gemini.available ? 'operational' : 'degraded',
            circuit_state: status.gemini.circuit_state,
          },
          openai: {
            status: status.openai.available ? 'operational' : 'disabled',
            circuit_state: status.openai.circuit_state,
          },
        },
      });
    } catch (error) {
      logger.error('Status endpoint error', {
        error: error instanceof Error ? error.message : 'Unknown error',
      });

      res.status(500).json({
        error: {
          code: 'INTERNAL_ERROR',
          message: 'Failed to get provider status',
        },
      });
    }
  }
);

export default router;
