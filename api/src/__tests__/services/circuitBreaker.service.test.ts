// ============================================================================
// Circuit Breaker Service Unit Tests
// Tests for circuit breaker pattern implementation
// ============================================================================

import {
  getCircuitState,
  shouldAllowRequest,
  recordSuccess,
  recordFailure,
  resetCircuit,
  getAllCircuitStatus,
  isProviderAvailable,
} from '../../services/circuitBreaker.service';
import * as redis from '../../services/redis.service';
import type { CircuitBreakerState } from '../../types';

// Access mock store for direct manipulation
const mockStore = (redis as unknown as { __mockStore: Map<string, { value: unknown; expiry?: number }> }).__mockStore;

describe('Circuit Breaker Service', () => {
  // -------------------------------------------------------------------------
  // getCircuitState Tests
  // -------------------------------------------------------------------------

  describe('getCircuitState', () => {
    it('should return default CLOSED state for new circuit', async () => {
      const state = await getCircuitState('gemini');

      expect(state.state).toBe('CLOSED');
      expect(state.failures).toBe(0);
      expect(state.successes).toBe(0);
      expect(state.lastFailure).toBeNull();
      expect(state.lastSuccess).toBeNull();
    });

    it('should return saved state from Redis', async () => {
      const savedState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 5,
        successes: 0,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() + 30000).toISOString() as unknown as Date,
      };

      mockStore.set('circuit:gemini:state', { value: savedState });

      const state = await getCircuitState('gemini');

      expect(state.state).toBe('OPEN');
      expect(state.failures).toBe(5);
    });

    it('should handle both gemini and openai providers', async () => {
      const geminiState = await getCircuitState('gemini');
      const openaiState = await getCircuitState('openai');

      expect(geminiState.state).toBe('CLOSED');
      expect(openaiState.state).toBe('CLOSED');
    });
  });

  // -------------------------------------------------------------------------
  // shouldAllowRequest Tests
  // -------------------------------------------------------------------------

  describe('shouldAllowRequest', () => {
    it('should allow request when circuit is CLOSED', async () => {
      const allowed = await shouldAllowRequest('gemini');

      expect(allowed).toBe(true);
    });

    it('should block request when circuit is OPEN and timeout not passed', async () => {
      const savedState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 5,
        successes: 0,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() + 60000).toISOString() as unknown as Date, // 60 seconds in future
      };

      mockStore.set('circuit:gemini:state', { value: savedState });

      const allowed = await shouldAllowRequest('gemini');

      expect(allowed).toBe(false);
    });

    it('should allow request when circuit is OPEN and timeout has passed', async () => {
      const savedState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 5,
        successes: 0,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() - 1000).toISOString() as unknown as Date, // 1 second in past
      };

      mockStore.set('circuit:gemini:state', { value: savedState });

      const allowed = await shouldAllowRequest('gemini');

      expect(allowed).toBe(true);
    });

    it('should allow limited requests in HALF_OPEN state', async () => {
      const savedState: CircuitBreakerState = {
        state: 'HALF_OPEN',
        failures: 0,
        successes: 1, // Less than halfOpenRequests threshold (3)
        lastFailure: null,
        lastSuccess: new Date().toISOString() as unknown as Date,
        nextAttempt: null,
      };

      mockStore.set('circuit:gemini:state', { value: savedState });

      const allowed = await shouldAllowRequest('gemini');

      expect(allowed).toBe(true);
    });

    it('should block requests when HALF_OPEN request limit reached', async () => {
      const savedState: CircuitBreakerState = {
        state: 'HALF_OPEN',
        failures: 0,
        successes: 3, // Equal to halfOpenRequests threshold
        lastFailure: null,
        lastSuccess: new Date().toISOString() as unknown as Date,
        nextAttempt: null,
      };

      mockStore.set('circuit:gemini:state', { value: savedState });

      const allowed = await shouldAllowRequest('gemini');

      expect(allowed).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // recordSuccess Tests
  // -------------------------------------------------------------------------

  describe('recordSuccess', () => {
    it('should reset failure count on success in CLOSED state', async () => {
      // Set initial state with some failures
      const savedState: CircuitBreakerState = {
        state: 'CLOSED',
        failures: 2,
        successes: 5,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: null,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await recordSuccess('gemini');

      const state = await getCircuitState('gemini');
      expect(state.failures).toBe(0);
      expect(state.lastSuccess).not.toBeNull();
    });

    it('should increment success count in HALF_OPEN state', async () => {
      const savedState: CircuitBreakerState = {
        state: 'HALF_OPEN',
        failures: 0,
        successes: 1,
        lastFailure: null,
        lastSuccess: null,
        nextAttempt: null,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await recordSuccess('gemini');

      const state = await getCircuitState('gemini');
      expect(state.successes).toBe(2);
    });

    it('should transition to CLOSED after enough successes in HALF_OPEN', async () => {
      const savedState: CircuitBreakerState = {
        state: 'HALF_OPEN',
        failures: 0,
        successes: 2, // One more success will reach threshold of 3
        lastFailure: null,
        lastSuccess: null,
        nextAttempt: null,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await recordSuccess('gemini');

      const state = await getCircuitState('gemini');
      expect(state.state).toBe('CLOSED');
    });
  });

  // -------------------------------------------------------------------------
  // recordFailure Tests
  // -------------------------------------------------------------------------

  describe('recordFailure', () => {
    it('should increment failure count in CLOSED state', async () => {
      await recordFailure('gemini');

      const state = await getCircuitState('gemini');
      expect(state.failures).toBe(1);
      expect(state.lastFailure).not.toBeNull();
    });

    it('should open circuit after failure threshold in CLOSED state', async () => {
      // Set state just below threshold
      const savedState: CircuitBreakerState = {
        state: 'CLOSED',
        failures: 4, // Threshold is 5, one more failure opens it
        successes: 0,
        lastFailure: null,
        lastSuccess: null,
        nextAttempt: null,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await recordFailure('gemini');

      const state = await getCircuitState('gemini');
      expect(state.state).toBe('OPEN');
      expect(state.nextAttempt).not.toBeNull();
    });

    it('should immediately open circuit on any failure in HALF_OPEN state', async () => {
      const savedState: CircuitBreakerState = {
        state: 'HALF_OPEN',
        failures: 0,
        successes: 1,
        lastFailure: null,
        lastSuccess: new Date().toISOString() as unknown as Date,
        nextAttempt: null,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await recordFailure('gemini');

      const state = await getCircuitState('gemini');
      expect(state.state).toBe('OPEN');
    });

    it('should update failure count when already OPEN', async () => {
      const savedState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 5,
        successes: 0,
        lastFailure: null,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() + 30000).toISOString() as unknown as Date,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await recordFailure('gemini');

      const state = await getCircuitState('gemini');
      expect(state.state).toBe('OPEN');
      expect(state.failures).toBe(6);
    });
  });

  // -------------------------------------------------------------------------
  // resetCircuit Tests
  // -------------------------------------------------------------------------

  describe('resetCircuit', () => {
    it('should reset circuit to default state', async () => {
      // Set up an OPEN circuit
      const savedState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 10,
        successes: 0,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() + 60000).toISOString() as unknown as Date,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      await resetCircuit('gemini');

      const state = await getCircuitState('gemini');
      expect(state.state).toBe('CLOSED');
      expect(state.failures).toBe(0);
    });
  });

  // -------------------------------------------------------------------------
  // getAllCircuitStatus Tests
  // -------------------------------------------------------------------------

  describe('getAllCircuitStatus', () => {
    it('should return status for both providers', async () => {
      const status = await getAllCircuitStatus();

      expect(status.gemini).toBeDefined();
      expect(status.openai).toBeDefined();
      expect(status.gemini.state).toBe('CLOSED');
      expect(status.openai.state).toBe('CLOSED');
    });

    it('should reflect different states for each provider', async () => {
      // Set gemini to OPEN
      const geminiState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 5,
        successes: 0,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() + 30000).toISOString() as unknown as Date,
      };
      mockStore.set('circuit:gemini:state', { value: geminiState });

      const status = await getAllCircuitStatus();

      expect(status.gemini.state).toBe('OPEN');
      expect(status.openai.state).toBe('CLOSED');
    });
  });

  // -------------------------------------------------------------------------
  // isProviderAvailable Tests
  // -------------------------------------------------------------------------

  describe('isProviderAvailable', () => {
    it('should return true when circuit is CLOSED', async () => {
      const available = await isProviderAvailable('gemini');

      expect(available).toBe(true);
    });

    it('should return false when circuit is OPEN', async () => {
      const savedState: CircuitBreakerState = {
        state: 'OPEN',
        failures: 5,
        successes: 0,
        lastFailure: new Date().toISOString() as unknown as Date,
        lastSuccess: null,
        nextAttempt: new Date(Date.now() + 60000).toISOString() as unknown as Date,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      const available = await isProviderAvailable('gemini');

      expect(available).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // State Machine Tests
  // -------------------------------------------------------------------------

  describe('circuit breaker state machine', () => {
    it('should follow CLOSED -> OPEN -> HALF_OPEN -> CLOSED lifecycle', async () => {
      // Start in CLOSED
      let state = await getCircuitState('gemini');
      expect(state.state).toBe('CLOSED');

      // Record enough failures to open circuit
      for (let i = 0; i < 5; i++) {
        await recordFailure('gemini');
      }
      state = await getCircuitState('gemini');
      expect(state.state).toBe('OPEN');

      // Simulate timeout passing
      const openState: CircuitBreakerState = {
        ...state,
        nextAttempt: new Date(Date.now() - 1000).toISOString() as unknown as Date,
      };
      mockStore.set('circuit:gemini:state', { value: openState });

      // Request should transition to HALF_OPEN
      await shouldAllowRequest('gemini');
      state = await getCircuitState('gemini');
      expect(state.state).toBe('HALF_OPEN');

      // Record enough successes to close circuit
      for (let i = 0; i < 3; i++) {
        await recordSuccess('gemini');
      }
      state = await getCircuitState('gemini');
      expect(state.state).toBe('CLOSED');
    });

    it('should follow HALF_OPEN -> OPEN on failure', async () => {
      // Start in HALF_OPEN
      const savedState: CircuitBreakerState = {
        state: 'HALF_OPEN',
        failures: 0,
        successes: 1,
        lastFailure: null,
        lastSuccess: new Date().toISOString() as unknown as Date,
        nextAttempt: null,
      };
      mockStore.set('circuit:gemini:state', { value: savedState });

      // One failure should open circuit again
      await recordFailure('gemini');

      const state = await getCircuitState('gemini');
      expect(state.state).toBe('OPEN');
    });
  });
});
