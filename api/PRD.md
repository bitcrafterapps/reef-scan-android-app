# ReefScan API Gateway PRD v2.0

## Tagline: Scalable, Secure AI Proxy for Reef Analysis

**Platform:** Cloud API (Multi-region deployment)
**Clients:** Android (Kotlin), iOS (Swift)
**Primary AI Backend:** Google Gemini 2.0 Flash Vision API
**Fallback AI Backend:** OpenAI GPT-4o Vision (disaster recovery)
**Goal:** Eliminate client-side rate limits, enable scale to 100K+ users
**Priority:** Reliability > Security > Latency > Cost
**SLA Target:** 99.9% uptime, <4s p95 latency

---

## Executive Summary

ReefScan's mobile apps currently call Gemini API directly, exposing API keys and hitting rate limits. This PRD defines a **centralized API Gateway** that:

1. **Eliminates rate limits** via API key pooling (3+ keys, 6,000+ RPM combined)
2. **Secures API keys** server-side with JWT authentication
3. **Ensures 99.9% uptime** via circuit breaker + OpenAI fallback
4. **Reduces costs** via image hash caching (estimated 20-30% savings)
5. **Supports scale** to 100K+ users (free: 3/day, premium: 20/day)

**Key Technical Decisions:**
- Edge deployment (Cloudflare Workers / Fly.io) for <50ms overhead
- Redis for rate limiting and session caching
- PostgreSQL for device registry and analytics
- Gemini 2.0 Flash primary, GPT-4o fallback

**Estimated Timeline:** 4 weeks to production
**Estimated Cost:** ~$420-580/month at 10K DAU (90% free, 10% premium)

---

## Table of Contents

1. [Problem Statement](#1-problem-statement)
2. [Solution Overview](#2-solution-overview)
3. [Authentication System](#3-authentication-system)
4. [API Key Pooling System](#4-api-key-pooling-system)
5. [Circuit Breaker & Failover](#5-circuit-breaker--failover-strategy)
6. [Rate Limiting Strategy](#6-rate-limiting-strategy)
7. [API Endpoints](#7-api-endpoints)
8. [Error Handling](#8-error-handling)
9. [Latency Optimization](#9-latency-optimization)
10. [Infrastructure Architecture](#10-infrastructure-architecture)
11. [Security Considerations](#11-security-considerations)
12. [Database Schema](#12-database-schema)
13. [Request Idempotency & Caching](#13-request-idempotency--caching)
14. [Privacy & GDPR](#14-privacy--data-handling-gdpr-compliant)
15. [Prompt Management](#15-prompt-management--versioning)
16. [Monitoring & Observability](#16-monitoring--observability)
17. [Client SDK Changes](#17-client-sdk-changes)
18. [Cost Analysis](#18-cost-analysis)
19. [Implementation Phases](#19-implementation-phases)
20. [Load Testing](#20-load-testing-specifications)
21. [Rollback Strategy](#21-rollback--migration-strategy)
22. [Success Metrics](#22-success-metrics)
23. [Analytics](#23-analytics--species-tracking)
24. [Open Questions](#24-open-questions)
25. [Appendix](#25-appendix)

---

## 1. Problem Statement

### Current State
- Both Android and iOS apps call Gemini API directly from client devices
- API keys are embedded in client apps (security risk)
- Single API key per platform hits rate limits quickly
- No centralized usage tracking or analytics
- Rate limit errors (HTTP 429) cause poor user experience

### Rate Limit Constraints (Gemini API)
| Tier | RPM | RPD | TPM |
|------|-----|-----|-----|
| Free | 5 | 25 | 100K |
| Paid Tier 1 | 2,000 | Unlimited | 4M |
| Paid Tier 2 ($250+) | 10,000 | Unlimited | 10M |

### Scale Requirements
- Target: 10,000 daily active users
- Max requests per user: 3/day (free) or 20/day (premium)
- Assumed tier mix: 90% free, 10% premium
- Peak daily requests: ~47,000 (9,000 × 3 + 1,000 × 20)
- Peak concurrent users: ~500 (assuming 5-minute sessions)
- Peak RPM needed: ~200-400 RPM (with usage spread)

---

## 2. Solution Overview

### Architecture Pattern
**API Gateway Proxy** with:
- JWT-based device authentication
- Server-side API key pooling (round-robin with smart fallback)
- Redis-based rate limiting per device
- Request queuing for burst handling
- Multi-region deployment for low latency

### High-Level Flow
```
┌─────────────┐     ┌─────────────────┐     ┌─────────────────┐     ┌─────────────┐
│ Mobile App  │────▶│  API Gateway    │────▶│  Key Pool       │────▶│ Gemini API  │
│ (iOS/Android)│◀────│  (Auth + Rate   │◀────│  Manager        │◀────│             │
└─────────────┘     │   Limiting)     │     └─────────────────┘     └─────────────┘
                    └─────────────────┘
                           │
                    ┌──────┴──────┐
                    │   Redis     │
                    │ (Rate Limits│
                    │  + Sessions)│
                    └─────────────┘
```

### Analyze Request Sequence Diagram

```
┌────────┐          ┌──────────┐          ┌───────┐          ┌────────┐          ┌────────┐
│ Client │          │ Gateway  │          │ Redis │          │ KeyPool│          │ Gemini │
└───┬────┘          └────┬─────┘          └───┬───┘          └───┬────┘          └───┬────┘
    │                    │                    │                  │                   │
    │ POST /v1/analyze   │                    │                  │                   │
    │ + JWT + Image      │                    │                  │                   │
    │───────────────────▶│                    │                  │                   │
    │                    │                    │                  │                   │
    │                    │ Validate JWT       │                  │                   │
    │                    │───────────────────▶│                  │                   │
    │                    │◀─ ─ ─ ─ ─ ─ ─ ─ ─ ─│                  │                   │
    │                    │                    │                  │                   │
    │                    │ Check Rate Limit   │                  │                   │
    │                    │───────────────────▶│                  │                   │
    │                    │◀─ ─ allowed ─ ─ ─ ─│                  │                   │
    │                    │                    │                  │                   │
    │                    │ Check Image Cache  │                  │                   │
    │                    │───────────────────▶│                  │                   │
    │                    │◀─ ─ miss ─ ─ ─ ─ ─│                  │                   │
    │                    │                    │                  │                   │
    │                    │ Get Available Key  │                  │                   │
    │                    │──────────────────────────────────────▶│                   │
    │                    │◀─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ key_1 ─ ─ ─ ─ ─│                   │
    │                    │                    │                  │                   │
    │                    │ generateContent    │                  │                   │
    │                    │─────────────────────────────────────────────────────────▶│
    │                    │                    │                  │                   │
    │                    │◀─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ AI Result ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
    │                    │                    │                  │                   │
    │                    │ Cache Result       │                  │                   │
    │                    │───────────────────▶│                  │                   │
    │                    │                    │                  │                   │
    │                    │ Increment Usage    │                  │                   │
    │                    │───────────────────▶│                  │                   │
    │                    │                    │                  │                   │
    │ 200 OK + Result    │                    │                  │                   │
    │◀───────────────────│                    │                  │                   │
    │                    │                    │                  │                   │
```

**Typical Latency Breakdown:**
| Step | Time |
|------|------|
| JWT Validation | ~5ms |
| Rate Limit Check | ~3ms |
| Cache Check | ~5ms |
| Key Selection | ~2ms |
| Gemini API Call | ~2000-3500ms |
| Response Parsing | ~10ms |
| **Total** | **~2025-3525ms** |

---

## 3. Authentication System

### Device Registration Flow

```
1. App First Launch:
   ┌──────────────┐                    ┌──────────────┐
   │  Mobile App  │───────────────────▶│  API Gateway │
   │              │  POST /register    │              │
   │              │  {device_uuid,     │              │
   │              │   platform,        │              │
   │              │   app_version,     │              │
   │              │   app_secret}      │              │
   │              │◀───────────────────│              │
   │              │  {access_token,    │              │
   │              │   refresh_token,   │              │
   │              │   expires_in}      │              │
   └──────────────┘                    └──────────────┘

2. Subsequent Requests:
   Authorization: Bearer <access_token>
```

### Token Specifications

| Token Type | Lifetime | Purpose |
|------------|----------|---------|
| Access Token (JWT) | 1 hour | API authentication |
| Refresh Token | 30 days | Obtain new access tokens |
| App Secret | Permanent | Validates legitimate app installs |

### JWT Payload
```json
{
  "sub": "device_uuid",
  "iat": 1702000000,
  "exp": 1702003600,
  "platform": "ios",
  "tier": "free",
  "daily_limit": 3,            // 3 for free, 20 for premium
  "subscription_id": null
}
```

### App Secret Validation
- Embedded in app binary (obfuscated)
- Different secrets per platform (iOS/Android)
- Secrets stored in API environment variables
- Rotatable without app update via versioned secrets

---

## 4. API Key Pooling System

### Pool Configuration
```yaml
gemini_key_pool:
  keys:
    - key: "AIza...key1"
      tier: "paid_tier_2"
      daily_quota: 10000
      rpm_limit: 10000
    - key: "AIza...key2"
      tier: "paid_tier_1"
      daily_quota: unlimited
      rpm_limit: 2000
    - key: "AIza...key3"
      tier: "paid_tier_1"
      daily_quota: unlimited
      rpm_limit: 2000
  selection_strategy: "weighted_round_robin"
  fallback_strategy: "next_available"
  cooldown_on_429: 60  # seconds
```

### Key Selection Algorithm
```python
def select_api_key():
    # 1. Filter out keys in cooldown (hit 429 recently)
    available_keys = [k for k in pool if not k.in_cooldown]

    # 2. Filter out keys at daily quota
    available_keys = [k for k in available_keys if k.requests_today < k.daily_quota]

    # 3. Sort by current RPM usage (least loaded first)
    available_keys.sort(key=lambda k: k.current_rpm)

    # 4. Weighted selection (higher tier keys get more traffic)
    weights = [k.tier_weight for k in available_keys]
    return weighted_random_choice(available_keys, weights)
```

### Key Health Monitoring
- Track success/failure rate per key
- Auto-disable keys with >5% error rate
- Alert on key exhaustion
- Daily quota reset at midnight Pacific Time

---

## 5. Circuit Breaker & Failover Strategy

### Circuit Breaker Pattern
Prevent cascading failures when Gemini API is degraded.

```
States:
┌─────────┐    failures >= 5     ┌─────────┐
│ CLOSED  │ ──────────────────▶  │  OPEN   │
│(normal) │                      │(failing)│
└────┬────┘                      └────┬────┘
     │                                │
     │    ◀── success                 │ after 30s
     │                                ▼
     │                          ┌──────────┐
     └────────────────────────  │HALF-OPEN │
            success             │ (testing)│
                                └──────────┘
```

### Circuit Breaker Configuration
```yaml
circuit_breaker:
  gemini:
    failure_threshold: 5          # failures before opening
    success_threshold: 3          # successes to close
    timeout_seconds: 30           # time in open state
    half_open_requests: 3         # test requests in half-open

  openai_fallback:
    enabled: true
    failure_threshold: 5
    timeout_seconds: 60
```

### Failover to OpenAI
When Gemini circuit is OPEN, automatically route to OpenAI GPT-4o Vision:

```python
async def analyze_image(image_data, mode):
    if gemini_circuit.is_closed():
        try:
            result = await call_gemini(image_data, mode)
            gemini_circuit.record_success()
            return result
        except GeminiError as e:
            gemini_circuit.record_failure()
            if gemini_circuit.is_open():
                return await call_openai_fallback(image_data, mode)
            raise
    else:
        # Gemini circuit is open, use fallback
        return await call_openai_fallback(image_data, mode)
```

### Fallback Provider Configuration
```yaml
fallback_providers:
  openai:
    enabled: true
    endpoint: "https://api.openai.com/v1/chat/completions"
    model: "gpt-4o"
    api_keys:
      - "sk-..."
    max_cost_per_day: 100.00  # USD limit
    priority: 1

  # Future: Add more providers
  anthropic:
    enabled: false
    model: "claude-3-5-sonnet"
```

### Health Status Response (Enhanced)
```json
{
  "status": "degraded",
  "version": "1.0.0",
  "providers": {
    "gemini": {
      "status": "circuit_open",
      "last_success": "2024-12-08T11:45:00Z",
      "failures_last_hour": 15
    },
    "openai": {
      "status": "active_fallback",
      "requests_today": 45
    }
  },
  "recommended_action": "retry_normal"
}
```

---

## 6. Rate Limiting Strategy

### Multi-Layer Rate Limiting

| Layer | Limit | Window | Action |
|-------|-------|--------|--------|
| Per Device (Free) | 3 requests | 24 hours | Hard block + upgrade prompt |
| Per Device (Premium) | 20 requests | 24 hours | Hard block + message |
| Per Device | 5 requests | 1 minute | Soft delay (queue) |
| Global | 500 RPM | 1 minute | Queue overflow |
| Per IP | 100 requests | 1 hour | Block + CAPTCHA |

### Implementation (Redis)
```lua
-- Sliding window rate limiter
local key = "ratelimit:" .. device_id .. ":" .. window
local current = redis.call("INCR", key)
if current == 1 then
    redis.call("EXPIRE", key, window_seconds)
end
if current > limit then
    return {0, redis.call("TTL", key)}  -- denied, retry_after
end
return {1, limit - current}  -- allowed, remaining
```

### Rate Limit Headers
```http
X-RateLimit-Limit: 3           // or 20 for premium
X-RateLimit-Remaining: 2
X-RateLimit-Reset: 1702089600
X-RateLimit-Window: daily
X-RateLimit-Tier: free         // or "premium"
```

### Tier-Based Rate Limiting Logic
```python
def get_daily_limit(device):
    """Determine daily limit based on subscription tier."""
    if device.tier == "premium" and device.subscription_active:
        return 20
    return 3  # Free tier default

async def check_rate_limit(device_id):
    device = await get_device(device_id)
    daily_limit = get_daily_limit(device)

    # Get current usage from Redis
    usage_key = f"usage:{device_id}:{today()}"
    current_usage = await redis.get(usage_key) or 0

    if current_usage >= daily_limit:
        return RateLimitResponse(
            allowed=False,
            limit=daily_limit,
            remaining=0,
            tier=device.tier,
            upgrade_url="https://reefscan.app/premium" if device.tier == "free" else None
        )

    return RateLimitResponse(
        allowed=True,
        limit=daily_limit,
        remaining=daily_limit - current_usage - 1,
        tier=device.tier
    )
```

### Subscription Tier Summary
| Tier | Daily Limit | Price | Features |
|------|-------------|-------|----------|
| Free | 3 scans/day | $0 | Basic identification |
| Premium | 20 scans/day | $X/month | All scan modes, history sync |

---

## 7. API Endpoints

### Authentication

#### `POST /v1/auth/register`
Register a new device and obtain tokens.

**Request:**
```json
{
  "device_uuid": "550e8400-e29b-41d4-a716-446655440000",
  "platform": "ios",
  "app_version": "1.0.0",
  "app_secret": "rs_ios_v1_xxxxx"
}
```

**Response (201):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

#### `POST /v1/auth/refresh`
Refresh an expired access token.

**Request:**
```json
{
  "refresh_token": "dGhpcyBpcyBhIHJlZnJlc2g..."
}
```

**Response (200):**
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "expires_in": 3600
}
```

---

### Reef Analysis

#### `POST /v1/analyze`
Analyze a reef tank image.

**Headers:**
```http
Authorization: Bearer <access_token>
Content-Type: application/json
X-Request-ID: <client-generated-uuid>
```

**Request:**
```json
{
  "image": {
    "data": "base64-encoded-jpeg",
    "mime_type": "image/jpeg"
  },
  "mode": "comprehensive",
  "options": {
    "include_recommendations": true,
    "language": "en"
  }
}
```

**Response (200):**
```json
{
  "request_id": "req_abc123",
  "tank_health": "Good",
  "summary": "Your reef tank appears healthy with thriving corals and fish.",
  "identifications": [
    {
      "name": "Ocellaris Clownfish (Amphiprion ocellaris)",
      "category": "Fish",
      "confidence": 95,
      "is_problem": false,
      "severity": null,
      "description": "Healthy adult clownfish with vibrant orange coloration"
    }
  ],
  "recommendations": [
    "Maintain current water parameters",
    "Consider adding additional flow for the SPS corals",
    "Monitor the small patch of green algae on the back glass"
  ],
  "usage": {
    "requests_today": 5,
    "daily_limit": 3,             // 3 for free, 20 for premium
    "reset_at": "2024-12-09T00:00:00Z"
  }
}
```

**Analysis Modes:**
| Mode | Description |
|------|-------------|
| `comprehensive` | Full tank analysis (default) |
| `fish_id` | Fish identification focus |
| `coral_id` | Coral & anemone focus |
| `algae_id` | Algae/bacteria/dino detection |
| `pest_id` | Pest & hitchhiker detection |

---

### Usage & Status

#### `GET /v1/usage`
Get current usage statistics.

**Response (200):**
```json
{
  "daily": {
    "used": 2,
    "limit": 3,                    // 3 for free, 20 for premium
    "reset_at": "2024-12-09T00:00:00Z"
  },
  "tier": "free",                  // "free" or "premium"
  "subscription_status": "none",   // "none", "active", "expired"
  "upgrade_url": "https://reefscan.app/premium"
}
```

#### `GET /v1/health`
API health check (no auth required).

**Response (200):**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "gemini_status": "operational",
  "latency_p95_ms": 2100
}
```

---

## 8. Error Handling

### Error Response Format
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Daily request limit exceeded",
    "details": {
      "limit": 3,
      "tier": "free",
      "upgrade_url": "https://reefscan.app/premium",
      "reset_at": "2024-12-09T00:00:00Z"
    },
    "retry_after": 3600
  }
}
```

### Error Codes

| Code | HTTP | Description | Client Action |
|------|------|-------------|---------------|
| `RATE_LIMIT_EXCEEDED` | 429 | Daily/minute limit hit | Show limit UI, wait |
| `INVALID_TOKEN` | 401 | Token expired/invalid | Refresh token |
| `INVALID_IMAGE` | 400 | Image too large/corrupt | Resize/retry |
| `AI_UNAVAILABLE` | 503 | Gemini API down | Retry with backoff |
| `INTERNAL_ERROR` | 500 | Server error | Retry with backoff |
| `DEVICE_BLOCKED` | 403 | Abuse detected | Contact support |

### Retry Strategy (Client)
```kotlin
val backoffMs = min(1000 * 2^retryCount, 30000) + random(0, 1000)
```

---

## 9. Latency Optimization

### Target Latencies
| Metric | Target | Max Acceptable |
|--------|--------|----------------|
| API Gateway overhead | <50ms | 100ms |
| Total request (p50) | <2.5s | 3.5s |
| Total request (p95) | <4.0s | 5.0s |
| Token validation | <5ms | 10ms |

### Optimization Techniques

1. **Edge Deployment**
   - Deploy to multiple regions (us-west, us-east, eu-west, ap-southeast)
   - Route to nearest region via GeoDNS
   - Gemini API is US-based, so US regions have lowest AI latency

2. **Connection Pooling**
   - Maintain persistent connections to Gemini API
   - HTTP/2 multiplexing for concurrent requests
   - Connection warmup on cold start

3. **Request Streaming**
   - Stream response from Gemini to client
   - First byte back faster for perceived performance

4. **Redis Caching**
   - Cache token validation results (5 min TTL)
   - Cache rate limit decisions
   - In-memory cache for hot paths

5. **Request Optimization**
   - Validate image size before forwarding
   - Compress request payloads (gzip)
   - Keep-alive connections

---

## 10. Infrastructure Architecture

### Recommended Stack

| Component | Technology | Justification |
|-----------|------------|---------------|
| Runtime | Node.js 20 / Bun | Low latency, async I/O |
| Framework | Fastify / Hono | Fastest Node frameworks |
| Cache/Rate Limit | Redis (Upstash) | Serverless-friendly, global |
| Database | PostgreSQL (Neon) | Device registry, audit logs |
| Hosting | Cloudflare Workers or Fly.io | Edge deployment, low latency |
| Secrets | Environment variables | Industry standard |
| Monitoring | Datadog / Axiom | Real-time observability |

### Alternative: Serverless
| Component | Technology |
|-----------|------------|
| Compute | AWS Lambda / Cloudflare Workers |
| API Gateway | AWS API Gateway / Cloudflare |
| Database | DynamoDB / Cloudflare D1 |
| Cache | ElastiCache / Upstash Redis |

### Deployment Architecture
```
                    ┌─────────────────────────────────┐
                    │        GeoDNS / Cloudflare      │
                    └──────────────┬──────────────────┘
                                   │
          ┌────────────────────────┼────────────────────────┐
          │                        │                        │
    ┌─────┴─────┐            ┌─────┴─────┐            ┌─────┴─────┐
    │ US-West   │            │ US-East   │            │ EU-West   │
    │  Region   │            │  Region   │            │  Region   │
    └─────┬─────┘            └─────┬─────┘            └─────┬─────┘
          │                        │                        │
    ┌─────┴─────┐            ┌─────┴─────┐            ┌─────┴─────┐
    │  Workers  │            │  Workers  │            │  Workers  │
    │  (2-10)   │            │  (2-10)   │            │  (2-10)   │
    └─────┬─────┘            └─────┬─────┘            └─────┬─────┘
          │                        │                        │
          └────────────────────────┼────────────────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │      Redis Cluster          │
                    │   (Rate Limits + Sessions)  │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────┴──────────────┐
                    │      PostgreSQL             │
                    │   (Device Registry)         │
                    └─────────────────────────────┘
```

---

## 11. Security Considerations

### API Security
- [x] HTTPS only (TLS 1.3)
- [x] JWT with short expiry (1 hour)
- [x] App secret validation
- [x] Rate limiting at multiple layers
- [x] Request signing (optional)
- [x] IP-based abuse detection

### Secret Management
- API keys stored in environment variables
- No secrets in code repository
- Key rotation without downtime
- Separate keys per environment (dev/staging/prod)

### Abuse Prevention
| Attack Vector | Mitigation |
|---------------|------------|
| API key extraction | Server-side only, app secret |
| Token replay | Short expiry, JTI tracking |
| DDoS | Cloudflare, rate limiting |
| Brute force | IP blocking, CAPTCHA |
| Image bomb | Size validation (max 5MB) |

---

## 12. Database Schema

### Device Registry Table
```sql
CREATE TABLE devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_uuid     VARCHAR(255) UNIQUE NOT NULL,  -- Client-provided UUID
    platform        VARCHAR(20) NOT NULL,          -- 'ios' | 'android'
    app_version     VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    last_seen_at    TIMESTAMPTZ DEFAULT NOW(),
    refresh_token   VARCHAR(512),                  -- Hashed
    token_version   INTEGER DEFAULT 1,             -- Increment to invalidate tokens
    tier            VARCHAR(20) DEFAULT 'free',    -- 'free' | 'premium'
    subscription_id VARCHAR(255),                  -- RevenueCat subscription ID
    is_blocked      BOOLEAN DEFAULT FALSE,
    block_reason    TEXT,
    metadata        JSONB DEFAULT '{}'             -- Additional device info
);

CREATE INDEX idx_devices_device_uuid ON devices(device_uuid);
CREATE INDEX idx_devices_last_seen ON devices(last_seen_at);
```

### Request Log Table (for analytics & debugging)
```sql
CREATE TABLE request_logs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       UUID REFERENCES devices(id),
    request_id      VARCHAR(255) UNIQUE NOT NULL,  -- Idempotency key
    mode            VARCHAR(50) NOT NULL,
    image_hash      VARCHAR(64),                   -- SHA-256 for dedup
    provider_used   VARCHAR(50),                   -- 'gemini' | 'openai'
    api_key_id      VARCHAR(50),                   -- Which key was used
    status          VARCHAR(20),                   -- 'success' | 'error'
    latency_ms      INTEGER,
    tokens_input    INTEGER,
    tokens_output   INTEGER,
    error_code      VARCHAR(50),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX idx_logs_device_id ON request_logs(device_id);
CREATE INDEX idx_logs_created_at ON request_logs(created_at);
CREATE INDEX idx_logs_image_hash ON request_logs(image_hash);
```

### Daily Usage Table (denormalized for fast queries)
```sql
CREATE TABLE daily_usage (
    device_id       UUID REFERENCES devices(id),
    date            DATE NOT NULL,
    request_count   INTEGER DEFAULT 0,
    PRIMARY KEY (device_id, date)
);

CREATE INDEX idx_daily_usage_date ON daily_usage(date);
```

### Image Cache Table (cost optimization)
```sql
CREATE TABLE image_cache (
    image_hash      VARCHAR(64) PRIMARY KEY,       -- SHA-256
    mode            VARCHAR(50) NOT NULL,
    result          JSONB NOT NULL,                -- Cached analysis result
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,                   -- TTL for cache
    hit_count       INTEGER DEFAULT 0
);

CREATE INDEX idx_cache_expires ON image_cache(expires_at);
```

---

## 13. Request Idempotency & Caching

### Idempotency Key Strategy
Prevent duplicate charges and duplicate analysis for the same request.

```
Client generates: X-Request-ID = UUID v4
Server checks: Has this request_id been processed?
  - Yes → Return cached result (no charge)
  - No  → Process and store result
```

### Implementation
```python
async def handle_analyze_request(request_id, device_id, image_data, mode):
    # Check for existing request
    existing = await db.query(
        "SELECT result FROM request_logs WHERE request_id = $1",
        request_id
    )
    if existing:
        return existing.result  # Return cached, no rate limit charge

    # Check for cached image result (same image, same mode)
    image_hash = sha256(image_data)
    cached = await db.query(
        "SELECT result FROM image_cache WHERE image_hash = $1 AND mode = $2",
        image_hash, mode
    )
    if cached:
        # Log request but don't charge rate limit
        await log_cache_hit(request_id, device_id, image_hash)
        return cached.result

    # Process new request
    result = await analyze_with_ai(image_data, mode)

    # Store for idempotency (24h TTL)
    await store_request_result(request_id, device_id, image_hash, result)

    # Store in image cache (7 day TTL)
    await cache_image_result(image_hash, mode, result)

    return result
```

### Cache TTL Strategy
| Cache Type | TTL | Purpose |
|------------|-----|---------|
| Request idempotency | 24 hours | Prevent duplicate requests |
| Image hash cache | 7 days | Save costs on identical images |
| Rate limit data | Rolling window | Accurate rate limiting |
| JWT validation | 5 minutes | Performance optimization |

### Cache Invalidation
- Image cache cleared on prompt version change
- Request logs archived after 30 days
- Expired cache entries cleaned via cron (hourly)

---

## 14. Privacy & Data Handling (GDPR Compliant)

### Data Classification
| Data Type | Sensitivity | Retention | Purpose |
|-----------|-------------|-----------|---------|
| Device UUID | Low | Account lifetime | Device identification |
| IP Address | Medium | 24 hours | Abuse prevention |
| Images | High | Not stored (streaming) | Analysis only |
| Analysis Results | Medium | 7 days (cache) | Performance |
| Usage Logs | Low | 90 days | Analytics, debugging |

### Image Data Policy
```
CRITICAL: Images are NOT permanently stored.

Flow:
1. Client sends image (base64)
2. Server forwards to AI provider
3. AI returns analysis
4. Image hash stored (for caching), image discarded
5. Only the result JSON is cached

Exception: Image hash (SHA-256) stored for:
- Cache lookup (cost saving)
- Abuse detection (same image spam)
```

### GDPR Compliance Features
1. **Right to Erasure**: `DELETE /v1/account` removes all device data
2. **Data Portability**: `GET /v1/account/export` returns all stored data
3. **Consent**: App must obtain consent before first scan
4. **Data Minimization**: Only essential data collected

### API Endpoints for Privacy
```
DELETE /v1/account
  - Deletes device registration
  - Invalidates all tokens
  - Removes usage history
  - Response: 204 No Content

GET /v1/account/export
  - Returns all data associated with device
  - Response: JSON with device info, usage logs
```

### Data Processing Agreement
- Gemini API: Google's data processing terms apply
- OpenAI API: OpenAI's data processing terms apply
- Both configured to NOT use data for training

---

## 15. Prompt Management & Versioning

### Prompt Storage Strategy
System prompts are large (~400 lines) and must be version-controlled.

```
Options:
├── Option A: Embedded in code (current)
│   Pros: Simple, no external dependency
│   Cons: Requires deploy to update
│
├── Option B: Database-stored ← RECOMMENDED
│   Pros: Update without deploy, A/B testing
│   Cons: Additional complexity
│
└── Option C: External file (S3/GCS)
    Pros: Easy editing, version control
    Cons: Additional latency, dependency
```

### Prompt Schema (Option B)
```sql
CREATE TABLE prompts (
    id              SERIAL PRIMARY KEY,
    name            VARCHAR(100) UNIQUE NOT NULL,  -- 'comprehensive', 'fish_id', etc.
    version         INTEGER NOT NULL,
    system_prompt   TEXT NOT NULL,
    mode_prompt     TEXT NOT NULL,
    is_active       BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    created_by      VARCHAR(100)
);

-- Only one active version per prompt name
CREATE UNIQUE INDEX idx_active_prompt ON prompts(name) WHERE is_active = TRUE;
```

### Prompt Versioning Flow
```
1. Developer creates new prompt version (is_active = false)
2. Test with internal accounts
3. Activate new version: UPDATE SET is_active = TRUE
4. Old responses cached with old prompt still valid for TTL
5. Monitor for accuracy regression
```

### Prompt Configuration API (Internal)
```
POST /internal/prompts
  - Create new prompt version

PUT /internal/prompts/:name/activate/:version
  - Activate specific version

GET /internal/prompts/:name/versions
  - List all versions with metrics
```

---

## 16. Monitoring & Observability

### Key Metrics
| Metric | Alert Threshold |
|--------|-----------------|
| Request latency (p95) | >5s |
| Error rate | >1% |
| Gemini API errors | >0.5% |
| Key pool exhaustion | <2 keys available |
| Daily request volume | >80% capacity |

### Logging Strategy
```json
{
  "timestamp": "2024-12-08T12:00:00Z",
  "request_id": "req_abc123",
  "device_id": "dev_xyz",
  "platform": "ios",
  "endpoint": "/v1/analyze",
  "latency_ms": 2340,
  "gemini_key_used": "key_1",
  "gemini_latency_ms": 2100,
  "status": 200,
  "tokens_used": 1500
}
```

### Dashboards
1. **Real-time**: Request rate, latency, errors
2. **Usage**: Daily active users, requests per user
3. **API Keys**: Utilization per key, quota remaining
4. **Business**: Conversion, feature usage

---

## 17. Client SDK Changes

### Android Changes Required
```kotlin
// Before: Direct Gemini call
GeminiService.api.generateContent(apiKey, request)

// After: Gateway call
ReefScanApi.analyze(accessToken, imageBase64, mode)
```

**Files to Modify:**
- `GeminiRepository.kt` → `ReefScanApiRepository.kt`
- `GeminiService.kt` → `ReefScanApiService.kt`
- Add `AuthManager.kt` for token management
- Update `LoadingViewModel.kt` to use new repository

### iOS Changes Required
```swift
// Before: Direct Gemini call
try await GeminiService.analyzeImage(imageData, mode: mode)

// After: Gateway call
try await ReefScanAPI.analyze(image: imageData, mode: mode)
```

**Files to Modify:**
- `GeminiService.swift` → `ReefScanAPIService.swift`
- Add `AuthManager.swift` for token management
- Update `LoadingView.swift` to use new service

### Token Storage
- **Android**: EncryptedSharedPreferences
- **iOS**: Keychain

---

## 18. Cost Analysis

### Gemini API Costs (Paid Tier)
| Model | Input (per 1M tokens) | Output (per 1M tokens) |
|-------|----------------------|------------------------|
| Gemini 2.0 Flash | $0.10 | $0.40 |
| Gemini 2.5 Flash | $0.30 | $2.50 |

### Estimated Monthly Costs (10K DAU)
| Component | Monthly Cost |
|-----------|--------------|
| Gemini API (~47K req/day × 30 × $0.002) | ~$280 |
| Infrastructure (Fly.io/CF) | ~$50-100 |
| Redis (Upstash) | ~$20-50 |
| Database (Neon) | ~$20-50 |
| Monitoring | ~$50-100 |
| **Total** | **~$420-580/month** |

*Note: Assumes 90% free (3/day) + 10% premium (20/day) user mix*

### Cost Optimization
- Use Gemini 2.0 Flash (cheapest vision model)
- Batch API for non-urgent requests (50% discount)
- Cache identical image hashes
- Optimize prompt length

---

## 19. Implementation Phases

### Phase 1: Core API (Week 1)
- [ ] Project setup (Fastify/Hono + TypeScript)
- [ ] Authentication endpoints (register, refresh)
- [ ] JWT generation and validation
- [ ] Basic Gemini proxy endpoint
- [ ] Environment configuration

### Phase 2: Rate Limiting & Keys (Week 2)
- [ ] Redis integration
- [ ] Device rate limiting
- [ ] API key pool implementation
- [ ] Key selection algorithm
- [ ] Key health monitoring

### Phase 3: Production Ready (Week 3)
- [ ] Error handling & retry logic
- [ ] Logging & monitoring
- [ ] Multi-region deployment
- [ ] Load testing
- [ ] Security audit

### Phase 4: Client Integration (Week 4)
- [ ] Android SDK update
- [ ] iOS SDK update
- [ ] Migration path (old → new)
- [ ] QA testing

---

## 20. Load Testing Specifications

### Test Scenarios

| Scenario | Users | RPM | Duration | Pass Criteria |
|----------|-------|-----|----------|---------------|
| Baseline | 100 | 50 | 10 min | p95 < 4s, 0% errors |
| Normal Load | 500 | 200 | 30 min | p95 < 4s, <0.1% errors |
| Peak Load | 1,000 | 500 | 15 min | p95 < 5s, <0.5% errors |
| Stress Test | 2,000 | 1,000 | 10 min | Graceful degradation |
| Soak Test | 500 | 200 | 4 hours | No memory leaks |
| Spike Test | 100→1000→100 | Variable | 15 min | Recovery < 30s |

### Load Test Script (k6)
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const errorRate = new Rate('errors');
const analyzeLatency = new Trend('analyze_latency');

export const options = {
  scenarios: {
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '2m', target: 100 },
        { duration: '10m', target: 500 },
        { duration: '5m', target: 500 },
        { duration: '2m', target: 0 },
      ],
    },
  },
  thresholds: {
    'http_req_duration{endpoint:analyze}': ['p(95)<4000'],
    'errors': ['rate<0.01'],
  },
};

export default function () {
  // Register device (once per VU)
  const token = registerDevice();

  // Analyze image
  const analyzeRes = http.post(
    `${BASE_URL}/v1/analyze`,
    JSON.stringify({ image: TEST_IMAGE, mode: 'comprehensive' }),
    { headers: { Authorization: `Bearer ${token}` } }
  );

  check(analyzeRes, {
    'analyze status 200': (r) => r.status === 200,
    'analyze has result': (r) => r.json('tank_health') !== undefined,
  });

  analyzeLatency.add(analyzeRes.timings.duration);
  errorRate.add(analyzeRes.status !== 200);

  sleep(randomIntBetween(5, 15)); // Simulate real user behavior
}
```

### Performance Benchmarks
```
Environment: Production (3 regions, 10 workers each)

Baseline Results (target):
├── Auth endpoints:     p50 < 50ms,  p95 < 100ms
├── Analyze endpoint:   p50 < 2.5s,  p95 < 4.0s
├── Usage endpoint:     p50 < 30ms,  p95 < 50ms
└── Health endpoint:    p50 < 10ms,  p95 < 20ms

Resource Utilization:
├── CPU:        < 70% under normal load
├── Memory:     < 80% (no growth over time)
├── Redis ops:  < 10,000/sec
└── DB conns:   < 50 active connections
```

### Failure Injection Testing
| Test | Method | Expected Behavior |
|------|--------|-------------------|
| Gemini timeout | Delay proxy | Circuit opens, fallback activates |
| Redis down | Kill connection | Graceful degradation, local cache |
| DB down | Kill connection | Auth fails, returns 503 |
| High latency | Artificial delay | Queue requests, timeout at 30s |
| Key exhaustion | Use all keys | Error response, no crash |

---

## 21. Rollback & Migration Strategy

### Client Migration Phases

```
Phase 1: Parallel Operation (Week 1-2)
┌─────────────────────────────────────────────────────────┐
│  Old App Version        │  New App Version              │
│  (Direct Gemini)        │  (Via API Gateway)            │
│  ─────────────────      │  ──────────────────           │
│  Still works            │  Uses new endpoints           │
│  No changes needed      │  Feature flag enabled         │
└─────────────────────────────────────────────────────────┘

Phase 2: Gradual Rollout (Week 2-3)
┌─────────────────────────────────────────────────────────┐
│  10% → 25% → 50% → 100% of new users                    │
│  Monitor error rates, latency, user feedback            │
│  Instant rollback if issues detected                    │
└─────────────────────────────────────────────────────────┘

Phase 3: Deprecation (Week 4+)
┌─────────────────────────────────────────────────────────┐
│  Force update prompt for old app versions               │
│  Disable direct Gemini keys after 30 days               │
│  Archive old code paths                                 │
└─────────────────────────────────────────────────────────┘
```

### Feature Flags (Client-side)
```kotlin
// Android - RemoteConfig
val useApiGateway = Firebase.remoteConfig.getBoolean("use_api_gateway")

if (useApiGateway) {
    ReefScanApi.analyze(token, image, mode)
} else {
    GeminiService.analyze(apiKey, image, mode)  // Legacy
}
```

```swift
// iOS - RemoteConfig
let useApiGateway = RemoteConfig.remoteConfig()["use_api_gateway"].boolValue

if useApiGateway {
    try await ReefScanAPI.analyze(image: data, mode: mode)
} else {
    try await GeminiService.analyzeImage(data, mode: mode)  // Legacy
}
```

### Rollback Triggers
| Condition | Action | Duration |
|-----------|--------|----------|
| Error rate > 5% | Disable flag for new users | Immediate |
| p95 latency > 8s | Reduce rollout percentage | 1 hour |
| User complaints spike | Manual rollback decision | Case-by-case |
| Security incident | Full rollback | Immediate |

### Rollback Procedure
```
1. Disable feature flag (Firebase RemoteConfig)
   → All clients fall back to direct Gemini

2. Investigate issue in API Gateway

3. Fix and redeploy

4. Re-enable flag at reduced percentage

5. Gradual increase with monitoring
```

### Data Migration
No data migration needed - new system creates fresh device records.
Old app local storage remains unchanged.

### Backward Compatibility
- API v1 will be supported for minimum 12 months
- Breaking changes only in v2+
- Deprecation notices 90 days in advance

---

## 22. Success Metrics

### Technical KPIs

| Metric | Target |
|--------|--------|
| Uptime | 99.9% |
| Latency (p95) | <4s |
| Error rate | <0.5% |
| Rate limit errors | <1% of requests |

### Business KPIs

| Metric | Target |
|--------|--------|
| Zero rate limit errors for users | 100% |
| API key cost efficiency | <$0.01/request |
| Client migration completion | 100% in 2 weeks |

---

## 23. Analytics & Species Tracking

### Species Identification Analytics Schema

```sql
CREATE TABLE species_analytics (
    id              SERIAL PRIMARY KEY,
    species_name    VARCHAR(255) NOT NULL,
    category        VARCHAR(50) NOT NULL,     -- Fish, Coral, Pest, etc.
    count           INTEGER DEFAULT 1,
    first_seen      TIMESTAMPTZ DEFAULT NOW(),
    last_seen       TIMESTAMPTZ DEFAULT NOW(),
    avg_confidence  DECIMAL(5,2),
    UNIQUE(species_name, category)
);

-- Increment on each identification
CREATE OR REPLACE FUNCTION update_species_analytics()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO species_analytics (species_name, category, count, avg_confidence)
    VALUES (NEW.name, NEW.category, 1, NEW.confidence)
    ON CONFLICT (species_name, category)
    DO UPDATE SET
        count = species_analytics.count + 1,
        last_seen = NOW(),
        avg_confidence = (species_analytics.avg_confidence + NEW.confidence) / 2;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
```

### Analytics Dashboard Queries

```sql
-- Top 10 most identified species
SELECT species_name, category, count, avg_confidence
FROM species_analytics
ORDER BY count DESC
LIMIT 10;

-- Problem species (pests, diseases) frequency
SELECT species_name, count, last_seen
FROM species_analytics
WHERE category IN ('Pest', 'Disease', 'Algae')
ORDER BY count DESC;

-- Daily identification trends
SELECT DATE(created_at) as date, COUNT(*) as scans,
       COUNT(DISTINCT device_id) as unique_users
FROM request_logs
WHERE created_at > NOW() - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY date;
```

### Business Intelligence Metrics

| Metric | Description | Use Case |
|--------|-------------|----------|
| Popular Species | Most frequently identified | Content prioritization |
| Problem Trends | Common issues by region/time | Seasonal alerts |
| User Engagement | Scans per user over time | Retention analysis |
| Mode Usage | Which scan modes are popular | Feature development |
| Error Patterns | Common failure modes | Quality improvement |

---

## 24. Open Questions

1. **Multi-tenancy**: Should we support multiple apps/partners using this API?
2. **Premium tiers**: Different rate limits for paid subscribers?
3. **Offline mode**: Cache recent responses for replay?
4. **Webhooks**: Notify external systems of scans?
5. **Regional compliance**: CCPA for California users?
6. **Backup regions**: Additional regions (Asia-Pacific)?

### Decisions Made

| Question | Decision | Rationale |
|----------|----------|-----------|
| Primary AI | Gemini 2.0 Flash | Cost effective, good accuracy |
| Fallback AI | OpenAI GPT-4o | Reliability during outages |
| Auth method | JWT with device UUID | Stateless, scalable |
| Rate limiting | Redis sliding window | Fast, accurate |
| Hosting | Edge deployment | Low latency globally |

---

## 25. Appendix

### A. Sample Environment Variables

```bash
# App Secrets
APP_SECRET_IOS_V1=rs_ios_v1_xxxxxxxxxxxxx
APP_SECRET_ANDROID_V1=rs_android_v1_xxxxxxxxxxxxx

# JWT
JWT_SECRET=super-secret-256-bit-key
JWT_ISSUER=reefscan-api

# Gemini Keys (Pool)
GEMINI_KEY_1=AIzaSy...
GEMINI_KEY_2=AIzaSy...
GEMINI_KEY_3=AIzaSy...

# OpenAI Fallback
OPENAI_API_KEY=sk-...

# Redis
REDIS_URL=redis://default:xxx@xxx.upstash.io:6379

# Database
DATABASE_URL=postgresql://user:pass@host/db

# Feature Flags
ENABLE_OPENAI_FALLBACK=true
ENABLE_IMAGE_CACHING=true
```

### B. Request/Response Size Limits

| Field | Limit |
|-------|-------|
| Image (base64) | 5MB |
| Request body | 10MB |
| Response body | 1MB |
| Request timeout | 30s |

### C. Related Documentation

- [Gemini API Rate Limits](https://ai.google.dev/gemini-api/docs/rate-limits)
- [Gemini API Pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [JWT Best Practices](https://datatracker.ietf.org/doc/html/rfc8725)
- [GDPR Compliance Guide](https://gdpr.eu/checklist/)

### D. Glossary

| Term | Definition |
|------|------------|
| RPM | Requests per minute |
| TPM | Tokens per minute |
| RPD | Requests per day |
| JWT | JSON Web Token |
| Circuit Breaker | Pattern to prevent cascading failures |
| Idempotency | Same request produces same result |

---

*Document Version: 2.0*
*Last Updated: December 8, 2024*
*Author: ReefScan Engineering*
*Review Status: Final*
