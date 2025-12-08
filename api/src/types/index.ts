// ============================================================================
// ReefScan API Types
// Based on PRD.md specifications
// ============================================================================

// -----------------------------------------------------------------------------
// Authentication Types
// -----------------------------------------------------------------------------

export interface JWTPayload {
  sub: string; // device_uuid
  iat: number;
  exp: number;
  platform: 'ios' | 'android';
  tier: 'free' | 'premium';
  daily_limit: number;
  subscription_id: string | null;
}

export interface DeviceRegistration {
  device_uuid: string;
  platform: 'ios' | 'android';
  app_version: string;
  app_secret: string;
}

export interface TokenResponse {
  access_token: string;
  refresh_token: string;
  token_type: 'Bearer';
  expires_in: number;
}

export interface RefreshTokenRequest {
  refresh_token: string;
}

// -----------------------------------------------------------------------------
// Device Types
// -----------------------------------------------------------------------------

export interface Device {
  id: string;
  device_uuid: string;
  platform: 'ios' | 'android';
  app_version: string;
  created_at: Date;
  last_seen_at: Date;
  refresh_token: string | null;
  token_version: number;
  tier: 'free' | 'premium';
  subscription_id: string | null;
  is_blocked: boolean;
  block_reason: string | null;
  metadata: Record<string, unknown>;
}

// -----------------------------------------------------------------------------
// Analysis Types
// -----------------------------------------------------------------------------

export type AnalysisMode =
  | 'comprehensive'
  | 'fish_id'
  | 'coral_id'
  | 'algae_id'
  | 'pest_id';

export interface AnalyzeRequest {
  image: {
    data: string; // base64 encoded
    mime_type: 'image/jpeg' | 'image/png';
  };
  mode: AnalysisMode;
  options?: {
    include_recommendations?: boolean;
    language?: string;
  };
}

export interface Identification {
  name: string;
  category: string;
  confidence: number;
  is_problem: boolean;
  severity: 'low' | 'medium' | 'high' | null;
  description: string;
}

export interface ScanResult {
  request_id: string;
  tank_health: 'Excellent' | 'Good' | 'Fair' | 'Needs Attention' | 'Critical';
  summary: string;
  identifications: Identification[];
  recommendations: string[];
  usage: UsageInfo;
}

export interface UsageInfo {
  requests_today: number;
  daily_limit: number;
  reset_at: string; // ISO date string
}

// -----------------------------------------------------------------------------
// Rate Limiting Types
// -----------------------------------------------------------------------------

export interface RateLimitResult {
  allowed: boolean;
  limit: number;
  remaining: number;
  reset_at: number; // Unix timestamp
  tier: 'free' | 'premium';
  upgrade_url?: string;
}

export interface RateLimitHeaders {
  'X-RateLimit-Limit': string;
  'X-RateLimit-Remaining': string;
  'X-RateLimit-Reset': string;
  'X-RateLimit-Window': string;
  'X-RateLimit-Tier': string;
}

// -----------------------------------------------------------------------------
// API Key Pool Types
// -----------------------------------------------------------------------------

export interface ApiKeyConfig {
  key: string;
  id: string;
  tier: 'paid_tier_1' | 'paid_tier_2';
  rpmLimit: number;
  dailyQuota: number | null; // null = unlimited
}

export interface ApiKeyState extends ApiKeyConfig {
  inCooldown: boolean;
  cooldownUntil: Date | null;
  requestsToday: number;
  currentRpm: number;
  errorRate: number;
}

// -----------------------------------------------------------------------------
// Circuit Breaker Types
// -----------------------------------------------------------------------------

export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

export interface CircuitBreakerConfig {
  failureThreshold: number;
  successThreshold: number;
  timeoutSeconds: number;
  halfOpenRequests: number;
}

export interface CircuitBreakerState {
  state: CircuitState;
  failures: number;
  successes: number;
  lastFailure: Date | null;
  lastSuccess: Date | null;
  nextAttempt: Date | null;
}

// -----------------------------------------------------------------------------
// Error Types
// -----------------------------------------------------------------------------

export type ErrorCode =
  | 'RATE_LIMIT_EXCEEDED'
  | 'INVALID_TOKEN'
  | 'INVALID_IMAGE'
  | 'AI_UNAVAILABLE'
  | 'INTERNAL_ERROR'
  | 'DEVICE_BLOCKED'
  | 'INVALID_REQUEST'
  | 'UNAUTHORIZED';

export interface ApiError {
  error: {
    code: ErrorCode;
    message: string;
    details?: Record<string, unknown>;
    retry_after?: number;
  };
}

// -----------------------------------------------------------------------------
// Health Check Types
// -----------------------------------------------------------------------------

export interface HealthStatus {
  status: 'healthy' | 'degraded' | 'unhealthy';
  version: string;
  providers: {
    gemini: ProviderStatus;
    openai: ProviderStatus;
  };
  database: 'connected' | 'disconnected';
  redis: 'connected' | 'disconnected';
  latency_p95_ms?: number;
}

export interface ProviderStatus {
  status: 'operational' | 'degraded' | 'circuit_open' | 'disabled';
  last_success?: string;
  failures_last_hour?: number;
  requests_today?: number;
}

// -----------------------------------------------------------------------------
// Usage & Analytics Types
// -----------------------------------------------------------------------------

export interface UsageResponse {
  daily: {
    used: number;
    limit: number;
    reset_at: string;
  };
  tier: 'free' | 'premium';
  subscription_status: 'none' | 'active' | 'expired';
  upgrade_url?: string;
}

export interface RequestLog {
  id: string;
  device_id: string;
  request_id: string;
  mode: AnalysisMode;
  image_hash: string | null;
  provider_used: 'gemini' | 'openai';
  api_key_id: string;
  status: 'success' | 'error';
  latency_ms: number;
  tokens_input: number;
  tokens_output: number;
  error_code: string | null;
  created_at: Date;
}

// -----------------------------------------------------------------------------
// Prompt Types
// -----------------------------------------------------------------------------

export interface Prompt {
  id: number;
  name: string;
  version: number;
  system_prompt: string;
  mode_prompt: string;
  is_active: boolean;
  created_at: Date;
  created_by: string | null;
}

// -----------------------------------------------------------------------------
// Cache Types
// -----------------------------------------------------------------------------

export interface CachedResult {
  image_hash: string;
  mode: AnalysisMode;
  result: ScanResult;
  created_at: Date;
  expires_at: Date;
  hit_count: number;
}

// -----------------------------------------------------------------------------
// Express Extensions
// -----------------------------------------------------------------------------

declare global {
  namespace Express {
    interface Request {
      device?: Device;
      jwt?: JWTPayload;
      requestId?: string;
    }
  }
}
