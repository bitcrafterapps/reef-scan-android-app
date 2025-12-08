// ============================================================================
// Structured Logger
// JSON-formatted logs for observability
// ============================================================================

type LogLevel = 'debug' | 'info' | 'warn' | 'error';

interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  request_id?: string;
  device_id?: string;
  [key: string]: unknown;
}

class Logger {
  private format(level: LogLevel, message: string, meta?: Record<string, unknown>): LogEntry {
    return {
      timestamp: new Date().toISOString(),
      level,
      message,
      ...meta,
    };
  }

  debug(message: string, meta?: Record<string, unknown>): void {
    if (process.env.NODE_ENV === 'development') {
      console.log(JSON.stringify(this.format('debug', message, meta)));
    }
  }

  info(message: string, meta?: Record<string, unknown>): void {
    console.log(JSON.stringify(this.format('info', message, meta)));
  }

  warn(message: string, meta?: Record<string, unknown>): void {
    console.warn(JSON.stringify(this.format('warn', message, meta)));
  }

  error(message: string, meta?: Record<string, unknown>): void {
    console.error(JSON.stringify(this.format('error', message, meta)));
  }

  // Log API request completion
  request(
    requestId: string,
    method: string,
    path: string,
    status: number,
    durationMs: number,
    meta?: Record<string, unknown>
  ): void {
    this.info('Request completed', {
      request_id: requestId,
      method,
      path,
      status,
      duration_ms: durationMs,
      ...meta,
    });
  }

  // Log Gemini API call
  geminiCall(
    requestId: string,
    keyId: string,
    latencyMs: number,
    success: boolean,
    tokensUsed?: number
  ): void {
    this.info('Gemini API call', {
      request_id: requestId,
      provider: 'gemini',
      api_key_id: keyId,
      latency_ms: latencyMs,
      success,
      tokens_used: tokensUsed,
    });
  }

  // Log rate limit event
  rateLimit(
    deviceId: string,
    tier: string,
    limit: number,
    remaining: number,
    blocked: boolean
  ): void {
    this.info('Rate limit check', {
      device_id: deviceId,
      tier,
      limit,
      remaining,
      blocked,
    });
  }

  // Log circuit breaker state change
  circuitBreaker(provider: string, previousState: string, newState: string): void {
    this.warn('Circuit breaker state change', {
      provider,
      previous_state: previousState,
      new_state: newState,
    });
  }
}

export const logger = new Logger();
export default logger;
