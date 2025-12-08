// ============================================================================
// AI Provider Orchestration Service
// Routes requests through Gemini with OpenAI fallback using circuit breaker
// ============================================================================

import * as keyPool from './keyPool.service';
import * as circuitBreaker from './circuitBreaker.service';
import * as gemini from './gemini.service';
import * as openai from './openai.service';
import type { AnalysisMode, ScanResult } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Types
// -----------------------------------------------------------------------------

export interface AnalysisResponse {
  success: boolean;
  result?: ScanResult;
  provider: 'gemini' | 'openai';
  apiKeyId?: string;
  tokensUsed: {
    input: number;
    output: number;
  };
  latencyMs: number;
  error?: {
    code: string;
    message: string;
  };
}

// -----------------------------------------------------------------------------
// Main Analysis Function
// -----------------------------------------------------------------------------

/**
 * Analyze an image using the best available AI provider
 * Implements circuit breaker pattern with automatic failover
 */
export async function analyzeImage(
  imageData: string,
  mimeType: 'image/jpeg' | 'image/png',
  mode: AnalysisMode,
  requestId: string
): Promise<AnalysisResponse> {
  const startTime = Date.now();

  // Try Gemini first (primary provider)
  const geminiResult = await tryGemini(imageData, mimeType, mode, requestId);

  if (geminiResult.success) {
    return {
      ...geminiResult,
      latencyMs: Date.now() - startTime,
    };
  }

  // Gemini failed, try OpenAI fallback
  logger.info('Falling back to OpenAI', {
    request_id: requestId,
    gemini_error: geminiResult.error?.code,
  });

  const openaiResult = await tryOpenAI(imageData, mimeType, mode, requestId);

  return {
    ...openaiResult,
    latencyMs: Date.now() - startTime,
  };
}

// -----------------------------------------------------------------------------
// Provider-Specific Functions
// -----------------------------------------------------------------------------

async function tryGemini(
  imageData: string,
  mimeType: 'image/jpeg' | 'image/png',
  mode: AnalysisMode,
  requestId: string
): Promise<Omit<AnalysisResponse, 'latencyMs'>> {
  // Check if Gemini circuit is open
  const geminiAvailable = await circuitBreaker.shouldAllowRequest('gemini');

  if (!geminiAvailable) {
    logger.warn('Gemini circuit breaker is open', { request_id: requestId });
    return {
      success: false,
      provider: 'gemini',
      tokensUsed: { input: 0, output: 0 },
      error: {
        code: 'CIRCUIT_OPEN',
        message: 'Gemini service is temporarily unavailable',
      },
    };
  }

  // Select an API key from the pool
  const selectedKey = await keyPool.selectKey();

  if (!selectedKey) {
    logger.warn('No API keys available', { request_id: requestId });
    return {
      success: false,
      provider: 'gemini',
      tokensUsed: { input: 0, output: 0 },
      error: {
        code: 'NO_KEYS_AVAILABLE',
        message: 'All API keys are in cooldown or at limit',
      },
    };
  }

  try {
    // Call Gemini API
    const result = await gemini.analyzeImage(imageData, mimeType, mode, selectedKey.key);

    if (result.success && result.result) {
      // Record success
      await Promise.all([
        keyPool.recordSuccess(selectedKey.id),
        circuitBreaker.recordSuccess('gemini'),
      ]);

      logger.geminiCall(
        requestId,
        selectedKey.id,
        0, // Latency tracked at higher level
        true,
        (result.tokensUsed?.input || 0) + (result.tokensUsed?.output || 0)
      );

      return {
        success: true,
        result: result.result,
        provider: 'gemini',
        apiKeyId: selectedKey.id,
        tokensUsed: result.tokensUsed || { input: 0, output: 0 },
      };
    }

    // API call returned but with error
    await Promise.all([
      keyPool.recordFailure(selectedKey.id, result.error?.statusCode),
      circuitBreaker.recordFailure('gemini'),
    ]);

    logger.geminiCall(requestId, selectedKey.id, 0, false);

    return {
      success: false,
      provider: 'gemini',
      apiKeyId: selectedKey.id,
      tokensUsed: { input: 0, output: 0 },
      error: result.error,
    };
  } catch (error) {
    // Unexpected error
    await Promise.all([
      keyPool.recordFailure(selectedKey.id),
      circuitBreaker.recordFailure('gemini'),
    ]);

    logger.geminiCall(requestId, selectedKey.id, 0, false);

    return {
      success: false,
      provider: 'gemini',
      apiKeyId: selectedKey.id,
      tokensUsed: { input: 0, output: 0 },
      error: {
        code: 'UNEXPECTED_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
      },
    };
  }
}

async function tryOpenAI(
  imageData: string,
  mimeType: 'image/jpeg' | 'image/png',
  mode: AnalysisMode,
  requestId: string
): Promise<Omit<AnalysisResponse, 'latencyMs'>> {
  // Check if OpenAI is available and circuit is not open
  const openaiAvailable = await circuitBreaker.shouldAllowRequest('openai');
  const openaiEnabled = await openai.isAvailable();

  if (!openaiAvailable) {
    logger.warn('OpenAI circuit breaker is open', { request_id: requestId });
    return {
      success: false,
      provider: 'openai',
      tokensUsed: { input: 0, output: 0 },
      error: {
        code: 'CIRCUIT_OPEN',
        message: 'OpenAI service is temporarily unavailable',
      },
    };
  }

  if (!openaiEnabled) {
    return {
      success: false,
      provider: 'openai',
      tokensUsed: { input: 0, output: 0 },
      error: {
        code: 'FALLBACK_UNAVAILABLE',
        message: 'OpenAI fallback is not available',
      },
    };
  }

  try {
    const result = await openai.analyzeImageFallback(imageData, mimeType, mode);

    if (result.success && result.result) {
      await circuitBreaker.recordSuccess('openai');

      logger.info('OpenAI fallback succeeded', {
        request_id: requestId,
        cost: result.cost,
      });

      return {
        success: true,
        result: result.result,
        provider: 'openai',
        tokensUsed: result.tokensUsed || { input: 0, output: 0 },
      };
    }

    await circuitBreaker.recordFailure('openai');

    return {
      success: false,
      provider: 'openai',
      tokensUsed: { input: 0, output: 0 },
      error: result.error,
    };
  } catch (error) {
    await circuitBreaker.recordFailure('openai');

    return {
      success: false,
      provider: 'openai',
      tokensUsed: { input: 0, output: 0 },
      error: {
        code: 'UNEXPECTED_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
      },
    };
  }
}

// -----------------------------------------------------------------------------
// Provider Status
// -----------------------------------------------------------------------------

/**
 * Get the status of all AI providers
 */
export async function getProviderStatus(): Promise<{
  gemini: {
    available: boolean;
    circuit_state: string;
    keys_available: number;
  };
  openai: {
    available: boolean;
    circuit_state: string;
    daily_cost: number;
    cost_limit: number;
  };
}> {
  const [geminiCircuit, openaiCircuit, keyMetrics, openaiCost] = await Promise.all([
    circuitBreaker.getCircuitState('gemini'),
    circuitBreaker.getCircuitState('openai'),
    keyPool.getKeyPoolMetrics(),
    openai.getCurrentDailyCost(),
  ]);

  const geminiAvailable =
    geminiCircuit.state !== 'OPEN' && keyMetrics.available_keys > 0;

  const openaiAvailable =
    openaiCircuit.state !== 'OPEN' &&
    await openai.isAvailable();

  return {
    gemini: {
      available: geminiAvailable,
      circuit_state: geminiCircuit.state,
      keys_available: keyMetrics.available_keys,
    },
    openai: {
      available: openaiAvailable,
      circuit_state: openaiCircuit.state,
      daily_cost: openaiCost,
      cost_limit: await import('../config').then((c) => c.default.openai.maxCostPerDay),
    },
  };
}

/**
 * Check if any AI provider is available
 */
export async function isAnyProviderAvailable(): Promise<boolean> {
  const status = await getProviderStatus();
  return status.gemini.available || status.openai.available;
}
