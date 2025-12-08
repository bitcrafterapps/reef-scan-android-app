# ReefScan API Implementation Checklist

**Stack:** Node.js + Express + TypeScript
**Hosting:** Vercel (Serverless Functions)
**Based on:** [PRD.md](./PRD.md)

---

## Phase 1: Project Setup & Infrastructure ✅ COMPLETED

### 1.1 Project Initialization
- [x] Create new directory structure for Vercel
  ```
  api/
  ├── src/
  │   ├── routes/
  │   ├── services/
  │   ├── middleware/
  │   ├── utils/
  │   ├── types/
  │   ├── db/
  │   └── config/
  ├── vercel.json
  ├── package.json
  └── tsconfig.json
  ```
- [x] Initialize npm project (`npm init -y`)
- [x] Install core dependencies:
  ```bash
  npm install express cors helmet
  npm install jsonwebtoken bcryptjs uuid
  npm install @vercel/postgres @upstash/redis
  npm install openai
  npm install zod  # validation
  ```
- [x] Install dev dependencies:
  ```bash
  npm install -D typescript @types/node @types/express
  npm install -D @types/jsonwebtoken @types/bcryptjs @types/uuid
  npm install -D ts-node nodemon
  npm install -D vercel
  ```
- [x] Configure TypeScript (`tsconfig.json`)
- [x] Configure ESLint + Prettier
- [x] Create `.env.example` with all required variables
- [x] Add `.gitignore` (node_modules, .env, .vercel)

### 1.2 Vercel Configuration
- [x] Create `vercel.json` configuration (with multi-region support: iad1, sfo1, cdg1)
- [ ] Set up Vercel project (`vercel link`) - *Requires Vercel account*
- [ ] Configure environment variables in Vercel dashboard - *Requires Vercel account*
- [ ] Set up preview and production environments - *Requires Vercel account*
- [ ] Configure custom domain (api.reefscan.app) - *Requires Vercel account*

### 1.3 Database Setup (Vercel Postgres)
- [x] Create database schema (`src/db/schema.sql`):
  - [x] `devices` table
  - [x] `request_logs` table
  - [x] `daily_usage` table
  - [x] `image_cache` table
  - [x] `prompts` table
  - [x] `species_analytics` table
- [x] Create database indexes
- [x] Create migration script (`src/db/migrate.ts`)
- [x] Create database utility functions (`src/db/index.ts`)
- [ ] Create Vercel Postgres database - *Requires Vercel account*
- [ ] Run database migrations - *Requires database connection*

### 1.4 Redis Setup (Upstash)
- [x] Create Redis service (`src/services/redis.service.ts`)
- [x] Configure Redis connection in environment (`.env.example`)
- [x] Create Redis utility functions (get, set, incr, hash operations)
- [ ] Create Upstash Redis database - *Requires Upstash account*
- [ ] Test Redis connection - *Requires Redis connection*

### 1.5 Additional Phase 1 Completions
- [x] Create TypeScript types (`src/types/index.ts`)
- [x] Create configuration module (`src/config/index.ts`)
- [x] Create structured logger (`src/utils/logger.ts`)
- [x] Create main Express application (`src/index.ts`)
- [x] Implement health check endpoint (`GET /health`)
- [x] TypeScript compiles without errors

---

## Phase 2: Core Authentication System ✅ COMPLETED

### 2.1 JWT Implementation
- [x] Create `src/services/auth.service.ts`
- [x] Implement JWT token generation:
  - [x] Access token (1 hour expiry)
  - [x] Refresh token (30 days expiry)
- [x] Define JWT payload structure:
  ```typescript
  interface JWTPayload {
    sub: string;        // device_uuid
    platform: string;
    tier: string;
    daily_limit: number;
    subscription_id: string | null;
  }
  ```
- [x] Implement JWT verification middleware (`src/middleware/auth.middleware.ts`)
- [x] Handle token expiration errors (TokenError class)

### 2.2 Device Registration
- [x] Create `src/routes/auth.routes.ts`
- [x] Implement `POST /v1/auth/register`:
  - [x] Validate request body (Zod schema)
  - [x] Validate app secret against env vars
  - [x] Check if device already exists
  - [x] Create device record in database
  - [x] Generate access + refresh tokens
  - [x] Return token response
- [x] Implement `POST /v1/auth/refresh`:
  - [x] Validate refresh token
  - [x] Check token version (for revocation)
  - [x] Generate new access token
  - [x] Return token response

### 2.3 App Secret Validation
- [x] Create `src/middleware/appSecret.middleware.ts`
- [x] Store secrets in environment:
  - [x] `APP_SECRET_IOS_V1`
  - [x] `APP_SECRET_ANDROID_V1`
- [x] Support versioned secrets for rotation
- [x] Log invalid secret attempts

### 2.4 Additional Auth Features Implemented
- [x] `POST /v1/auth/revoke` - Revoke all tokens (logout)
- [x] `GET /v1/auth/me` - Get current device info
- [x] Zod validation schemas (`src/utils/validation.ts`)
- [x] TypeScript compiles without errors

---

## Phase 3: Rate Limiting System ✅ COMPLETED

### 3.1 Redis Rate Limiter
- [x] Create `src/services/rateLimit.service.ts`
- [x] Implement sliding window rate limiter:
  ```typescript
  async function checkRateLimit(deviceId: string, tier: string): Promise<RateLimitResult>
  ```
- [x] Implement tier-based limits:
  - [x] Free: 3 requests/day
  - [x] Premium: 20 requests/day
  - [x] Per-minute: 5 requests/minute (all tiers)
- [x] Implement global rate limit (500 RPM)
- [x] Create rate limit middleware (`src/middleware/rateLimit.middleware.ts`)

### 3.2 Rate Limit Headers
- [x] Create `src/middleware/rateLimit.middleware.ts` (combined with rate limiting)
- [x] Add headers to all responses:
  - [x] `X-RateLimit-Limit`
  - [x] `X-RateLimit-Remaining`
  - [x] `X-RateLimit-Reset`
  - [x] `X-RateLimit-Window`
  - [x] `X-RateLimit-Tier`

### 3.3 Usage Tracking
- [x] Create `src/services/usage.service.ts`
- [x] Implement daily usage counter (Redis)
- [x] Implement usage persistence to Postgres
- [x] Create usage archive function (for cron jobs)

### 3.4 Additional Phase 3 Features
- [x] Create `src/routes/usage.routes.ts` with GET /v1/usage and /v1/usage/stats
- [x] Implement IP-based rate limiting (abuse prevention)
- [x] TypeScript compiles without errors

---

## Phase 4: API Key Pool Management ✅ COMPLETED

### 4.1 Key Pool Service
- [x] Create `src/services/keyPool.service.ts`
- [x] Define key pool configuration
- [x] Load keys from environment variables
- [x] Implement key selection algorithm:
  - [x] Filter keys in cooldown
  - [x] Filter keys at quota
  - [x] Sort by current RPM (least loaded)
  - [x] Weighted selection

### 4.2 Key Health Monitoring
- [x] Track success/failure rate per key (Redis)
- [x] Implement cooldown on 429 response (60s)
- [x] Auto-disable keys with >5% error rate
- [x] Implement daily quota reset

### 4.3 Key Metrics
- [x] Log key usage per request
- [x] Create key pool metrics function
- [x] Alert when <2 keys available

---

## Phase 5: Circuit Breaker & Failover ✅ COMPLETED

### 5.1 Circuit Breaker Implementation
- [x] Create `src/services/circuitBreaker.service.ts`
- [x] Implement circuit breaker states (CLOSED, OPEN, HALF_OPEN)
- [x] Configure thresholds (failure: 5, success: 3, timeout: 30s)
- [x] Store circuit state in Redis

### 5.2 Gemini API Integration
- [x] Create `src/services/gemini.service.ts`
- [x] Implement `analyzeImage()` function with all features
- [x] Parse response and extract JSON
- [x] Handle Gemini-specific errors

### 5.3 OpenAI Fallback
- [x] Create `src/services/openai.service.ts`
- [x] Implement `analyzeImageFallback()` function
- [x] Configure daily cost limit ($100)
- [x] Track fallback usage

### 5.4 Provider Orchestration
- [x] Create `src/services/aiProvider.service.ts`
- [x] Route based on circuit breaker state
- [x] Record success/failure for circuit

---

## Phase 6: Core API Endpoints ✅ COMPLETED

### 6.1 Analysis Endpoint
- [x] Create `src/routes/analyze.routes.ts`
- [x] Implement `POST /v1/analyze` with all features
- [x] Implement `GET /v1/analyze/status` for provider status

### 6.2 Request Validation
- [x] Create Zod schemas (`src/utils/validation.ts`)
- [x] Validate image size, mime type, analysis mode

### 6.3 Usage Endpoint
- [x] `GET /v1/usage` - daily stats, tier info, upgrade URL

### 6.4 Health Endpoint
- [x] `GET /health` - database, Redis, provider status

### 6.5 Account Endpoints (GDPR)
- [x] Create `src/routes/account.routes.ts`
- [x] `DELETE /v1/account` - delete all data
- [x] `GET /v1/account/export` - export all data
- [x] `GET /v1/account` - get account info

---

## Phase 7: Caching & Idempotency ✅ COMPLETED

### 7.1 Image Hash Caching
- [x] Create `src/services/cache.service.ts`
- [x] Implement SHA-256 image hash calculation
- [x] Check cache before AI call
- [x] Store results with 7-day TTL
- [x] Track cache hit count

### 7.2 Request Idempotency
- [x] Store request_id → result mapping
- [x] Return cached result for duplicate requests
- [x] 24-hour TTL for idempotency keys

### 7.3 Cache Invalidation
- [x] Cache invalidation function implemented
- [x] Clear cache on prompt version change supported

---

## Phase 8: Prompt Management ✅ COMPLETED

### 8.1 Prompt Storage
- [x] `prompts` table in database schema
- [x] Default prompts defined in code

### 8.2 Prompt Service
- [x] Create `src/services/prompt.service.ts`
- [x] `getActivePrompt(mode)` with Redis caching (5 min TTL)
- [x] Fall back to defaults if DB not configured
- [x] Support prompt versioning

### 8.3 Internal Prompt API (Optional)
- [x] Functions for creating/activating prompt versions
- [ ] REST endpoints (can add later if needed)

---

## Phase 9: Error Handling & Logging ✅ COMPLETED

### 9.1 Error Handler
- [x] Create `src/middleware/errorHandler.middleware.ts`
- [x] Consistent error response format
- [x] Custom error classes (AppError, ValidationError, etc.)
- [x] Map errors to HTTP status codes

### 9.2 Error Codes
- [x] All error codes implemented (RATE_LIMIT_EXCEEDED, INVALID_TOKEN, etc.)

### 9.3 Structured Logging
- [x] `src/utils/logger.ts` with JSON formatting
- [x] Request logging middleware
- [x] Gemini call logging
- [x] Rate limit event logging

---

## Phase 10: Security Hardening ✅ COMPLETED

### 10.1 Security Middleware
- [x] Helmet.js security headers
- [x] CORS configured for mobile apps
- [x] Request size limits (10MB)
- [x] Request timeout (30s)

### 10.2 Input Validation
- [x] Zod schemas for all requests
- [x] UUID format validation in schemas
- [x] Base64 image validation
- [x] Image size limits (5MB)

### 10.3 Abuse Prevention
- [x] IP-based rate limiting implemented
- [x] Device blocking support
- [x] Suspicious activity logging

---

## Phase 11: Monitoring & Observability ✅ COMPLETED

### 11.1 Metrics Collection
- [x] Key pool metrics endpoint
- [x] Circuit breaker state tracking
- [x] Provider status endpoint (`GET /v1/analyze/status`)

### 11.2 Health Dashboard
- [x] `GET /health` endpoint with full status
- [x] Provider availability status
- [x] Database/Redis connection status

---

## Phase 12: Testing ✅ COMPLETED

### 12.1 Unit Tests
- [x] Test JWT generation/validation (21 tests)
- [x] Test rate limit logic (20 tests)
- [x] Test key selection algorithm (23 tests)
- [x] Test circuit breaker state machine (22 tests)
- [x] Test image hash calculation (21 tests)

### 12.2 Integration Tests
- [x] Test auth flow (register → refresh → revoke)
- [x] Test usage endpoints
- [x] Test rate limiting headers
- [x] Test 404 handling
- [x] Test health check endpoint
- [ ] Test analyze flow (requires AI provider mocks)
- [ ] Test cache hit/miss (requires AI provider mocks)
- [ ] Test fallback to OpenAI (requires AI provider mocks)

### 12.3 Load Testing
- [ ] Set up k6 test scripts
- [ ] Run load tests after deployment

### Test Summary
- **Total Tests:** 125 passing
- **Unit Tests:** 107 tests across 5 service modules
- **Integration Tests:** 18 API endpoint tests

---

## Phase 13: Deployment 📋 REQUIRES INFRASTRUCTURE

### 13.1 Prerequisites
- [ ] Create Vercel account and link project
- [ ] Create Upstash Redis database
- [ ] Create Vercel Postgres database
- [ ] Run database migrations
- [ ] Set environment variables

### 13.2 Staging Deployment
- [ ] Deploy to Vercel preview
- [ ] Run smoke tests
- [ ] Verify all endpoints

### 13.3 Production Deployment
- [ ] Deploy to production
- [ ] Configure custom domain (api.reefscan.app)
- [ ] Verify DNS and SSL
- [ ] Monitor for 24 hours

---

## Phase 14: Client Integration Support 📋 POST-DEPLOYMENT

### 14.1 SDK Documentation
- [ ] Android integration guide
- [ ] iOS integration guide
- [ ] Error handling documentation

### 14.2 Migration Support
- [ ] Set up feature flag in Firebase Remote Config
- [ ] Test parallel operation (old + new)
- [ ] Document rollback procedure

---

## Environment Variables Checklist

```bash
# Authentication
JWT_SECRET=                    # 256-bit secret for signing JWTs
JWT_ISSUER=reefscan-api
APP_SECRET_IOS_V1=             # iOS app secret
APP_SECRET_ANDROID_V1=         # Android app secret

# Gemini API Keys (Pool)
GEMINI_KEY_1=                  # Primary key
GEMINI_KEY_2=                  # Secondary key
GEMINI_KEY_3=                  # Tertiary key

# OpenAI Fallback
OPENAI_API_KEY=                # For fallback

# Database (Vercel Postgres)
POSTGRES_URL=                  # Connection string
POSTGRES_PRISMA_URL=           # Prisma connection
POSTGRES_URL_NON_POOLING=      # Direct connection

# Redis (Upstash)
UPSTASH_REDIS_REST_URL=
UPSTASH_REDIS_REST_TOKEN=

# Feature Flags
ENABLE_OPENAI_FALLBACK=true
ENABLE_IMAGE_CACHING=true

# Monitoring (Optional)
AXIOM_TOKEN=
AXIOM_DATASET=
```

---

## Definition of Done

Each task is complete when:
- [ ] Code is written and type-safe
- [ ] Unit tests pass (where applicable)
- [ ] Code is reviewed
- [ ] Deployed to preview environment
- [ ] Manually tested
- [ ] Documentation updated

---

## Estimated Timeline

| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Phase 1: Setup | 2 days | None |
| Phase 2: Auth | 2 days | Phase 1 |
| Phase 3: Rate Limiting | 1 day | Phase 1, 2 |
| Phase 4: Key Pool | 1 day | Phase 1 |
| Phase 5: Circuit Breaker | 2 days | Phase 4 |
| Phase 6: API Endpoints | 2 days | Phase 2-5 |
| Phase 7: Caching | 1 day | Phase 6 |
| Phase 8: Prompts | 1 day | Phase 6 |
| Phase 9: Error Handling | 1 day | Phase 6 |
| Phase 10: Security | 1 day | Phase 6 |
| Phase 11: Monitoring | 1 day | Phase 6 |
| Phase 12: Testing | 2 days | Phase 1-11 |
| Phase 13: Deployment | 1 day | Phase 12 |
| Phase 14: Client Support | 2 days | Phase 13 |

**Total: ~20 working days (4 weeks)**

---

*Last Updated: December 8, 2024*
*Phase 12 Testing completed with 125 passing tests*
*Based on PRD v2.0*
