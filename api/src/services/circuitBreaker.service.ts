// ============================================================================
// Circuit Breaker Service
// Implements circuit breaker pattern for AI provider failover
// ============================================================================

import * as redis from './redis.service';
import config from '../config';
import type { CircuitState, CircuitBreakerState, CircuitBreakerConfig } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Redis Key Patterns
// -----------------------------------------------------------------------------

const KEYS = {
  circuitState: (provider: string) => `circuit:${provider}:state`,
};

// -----------------------------------------------------------------------------
// Circuit Breaker Implementation
// -----------------------------------------------------------------------------

/**
 * Get the current state of a circuit breaker
 */
export async function getCircuitState(provider: 'gemini' | 'openai'): Promise<CircuitBreakerState> {
  const key = KEYS.circuitState(provider);
  const savedState = await redis.get<CircuitBreakerState>(key);

  if (savedState) {
    // Convert date strings back to Date objects
    return {
      ...savedState,
      lastFailure: savedState.lastFailure ? new Date(savedState.lastFailure) : null,
      lastSuccess: savedState.lastSuccess ? new Date(savedState.lastSuccess) : null,
      nextAttempt: savedState.nextAttempt ? new Date(savedState.nextAttempt) : null,
    };
  }

  // Default state: CLOSED (healthy)
  return {
    state: 'CLOSED',
    failures: 0,
    successes: 0,
    lastFailure: null,
    lastSuccess: null,
    nextAttempt: null,
  };
}

/**
 * Save circuit breaker state to Redis
 */
async function saveCircuitState(
  provider: 'gemini' | 'openai',
  state: CircuitBreakerState
): Promise<void> {
  const key = KEYS.circuitState(provider);
  // Store for 1 hour (will be refreshed on activity)
  await redis.set(key, state, 3600);
}

/**
 * Check if a request should be allowed through the circuit
 */
export async function shouldAllowRequest(provider: 'gemini' | 'openai'): Promise<boolean> {
  const circuitState = await getCircuitState(provider);
  const circuitConfig = config.circuitBreaker[provider];

  switch (circuitState.state) {
    case 'CLOSED':
      // Circuit is healthy, allow all requests
      return true;

    case 'OPEN':
      // Circuit is open, check if timeout has passed
      if (circuitState.nextAttempt && new Date() >= circuitState.nextAttempt) {
        // Transition to HALF_OPEN
        await transitionTo(provider, 'HALF_OPEN', circuitState, circuitConfig);
        return true;
      }
      // Still in timeout, reject request
      return false;

    case 'HALF_OPEN':
      // Allow limited requests to test if service recovered
      // Only allow if we haven't exceeded half-open request limit
      return circuitState.successes < circuitConfig.halfOpenRequests;

    default:
      return true;
  }
}

/**
 * Record a successful request
 */
export async function recordSuccess(provider: 'gemini' | 'openai'): Promise<void> {
  const circuitState = await getCircuitState(provider);
  const circuitConfig = config.circuitBreaker[provider];

  switch (circuitState.state) {
    case 'CLOSED':
      // Reset failure count on success
      if (circuitState.failures > 0) {
        circuitState.failures = 0;
      }
      circuitState.lastSuccess = new Date();
      circuitState.successes++;
      await saveCircuitState(provider, circuitState);
      break;

    case 'HALF_OPEN':
      // Count successes in half-open state
      circuitState.successes++;
      circuitState.lastSuccess = new Date();

      // Check if we've had enough successes to close the circuit
      if (circuitState.successes >= circuitConfig.successThreshold) {
        await transitionTo(provider, 'CLOSED', circuitState, circuitConfig);
        logger.circuitBreaker(provider, 'HALF_OPEN', 'CLOSED');
      } else {
        await saveCircuitState(provider, circuitState);
      }
      break;

    case 'OPEN':
      // Shouldn't happen, but handle gracefully
      circuitState.lastSuccess = new Date();
      await saveCircuitState(provider, circuitState);
      break;
  }
}

/**
 * Record a failed request
 */
export async function recordFailure(provider: 'gemini' | 'openai'): Promise<void> {
  const circuitState = await getCircuitState(provider);
  const circuitConfig = config.circuitBreaker[provider];

  circuitState.failures++;
  circuitState.lastFailure = new Date();

  switch (circuitState.state) {
    case 'CLOSED':
      // Check if we've exceeded failure threshold
      if (circuitState.failures >= circuitConfig.failureThreshold) {
        await transitionTo(provider, 'OPEN', circuitState, circuitConfig);
        logger.circuitBreaker(provider, 'CLOSED', 'OPEN');
      } else {
        await saveCircuitState(provider, circuitState);
      }
      break;

    case 'HALF_OPEN':
      // Any failure in half-open state opens the circuit again
      await transitionTo(provider, 'OPEN', circuitState, circuitConfig);
      logger.circuitBreaker(provider, 'HALF_OPEN', 'OPEN');
      break;

    case 'OPEN':
      // Already open, just update failure count
      await saveCircuitState(provider, circuitState);
      break;
  }
}

/**
 * Transition circuit to a new state
 */
async function transitionTo(
  provider: 'gemini' | 'openai',
  newState: CircuitState,
  currentState: CircuitBreakerState,
  circuitConfig: CircuitBreakerConfig
): Promise<void> {
  const previousState = currentState.state;

  currentState.state = newState;

  switch (newState) {
    case 'OPEN':
      // Set timeout for when to try again
      currentState.nextAttempt = new Date(
        Date.now() + circuitConfig.timeoutSeconds * 1000
      );
      currentState.successes = 0;
      break;

    case 'HALF_OPEN':
      // Reset counters for testing
      currentState.successes = 0;
      currentState.failures = 0;
      currentState.nextAttempt = null;
      break;

    case 'CLOSED':
      // Reset all counters
      currentState.failures = 0;
      currentState.successes = 0;
      currentState.nextAttempt = null;
      break;
  }

  await saveCircuitState(provider, currentState);

  logger.info('Circuit breaker state transition', {
    provider,
    from: previousState,
    to: newState,
    next_attempt: currentState.nextAttempt?.toISOString(),
  });
}

/**
 * Force reset a circuit breaker (for admin/testing)
 */
export async function resetCircuit(provider: 'gemini' | 'openai'): Promise<void> {
  const key = KEYS.circuitState(provider);
  await redis.del(key);

  logger.info('Circuit breaker reset', { provider });
}

/**
 * Get circuit breaker status for all providers
 */
export async function getAllCircuitStatus(): Promise<{
  gemini: CircuitBreakerState;
  openai: CircuitBreakerState;
}> {
  const [geminiState, openaiState] = await Promise.all([
    getCircuitState('gemini'),
    getCircuitState('openai'),
  ]);

  return {
    gemini: geminiState,
    openai: openaiState,
  };
}

/**
 * Check if provider is available (circuit not open)
 */
export async function isProviderAvailable(provider: 'gemini' | 'openai'): Promise<boolean> {
  return shouldAllowRequest(provider);
}
