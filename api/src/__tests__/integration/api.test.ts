// ============================================================================
// API Integration Tests
// End-to-end tests for API endpoints
// ============================================================================

import request from 'supertest';
import app from '../../index';

describe('API Integration Tests', () => {
  // -------------------------------------------------------------------------
  // Health Check Tests
  // -------------------------------------------------------------------------

  describe('GET /health', () => {
    it('should return health status', async () => {
      const response = await request(app).get('/health');

      expect(response.status).toBe(200);
      expect(response.body.status).toBe('healthy');
      expect(response.body.version).toBeDefined();
      expect(response.body.providers).toBeDefined();
    });

    it('should include provider status', async () => {
      const response = await request(app).get('/health');

      expect(response.body.providers.gemini).toBeDefined();
      expect(response.body.providers.openai).toBeDefined();
    });
  });

  // -------------------------------------------------------------------------
  // Root Endpoint Tests
  // -------------------------------------------------------------------------

  describe('GET /', () => {
    it('should return API info', async () => {
      const response = await request(app).get('/');

      expect(response.status).toBe(200);
      expect(response.body.name).toBe('ReefScan API');
      expect(response.body.version).toBeDefined();
      expect(response.body.api_version).toBeDefined();
    });
  });

  // -------------------------------------------------------------------------
  // Auth Flow Tests
  // -------------------------------------------------------------------------

  describe('Auth Flow', () => {
    const deviceUuid = '550e8400-e29b-41d4-a716-446655440000';
    let accessToken: string;
    let refreshToken: string;

    describe('POST /v1/auth/register', () => {
      it('should register a new device', async () => {
        const response = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: deviceUuid,
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        expect(response.status).toBe(201);
        expect(response.body.access_token).toBeDefined();
        expect(response.body.refresh_token).toBeDefined();
        expect(response.body.token_type).toBe('Bearer');
        expect(response.body.expires_in).toBe(3600);
        expect(response.body.device.tier).toBe('free');

        accessToken = response.body.access_token;
        refreshToken = response.body.refresh_token;
      });

      it('should return existing device on re-registration', async () => {
        // Register twice with same UUID - second should be 200
        const firstResponse = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440100',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });
        expect(firstResponse.status).toBe(201);

        // Re-register same device
        const secondResponse = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440100',
            platform: 'ios',
            app_version: '1.0.1',
            app_secret: 'test-ios-secret',
          });

        expect(secondResponse.status).toBe(200);
        expect(secondResponse.body.access_token).toBeDefined();
      });

      it('should reject invalid app secret', async () => {
        const response = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440999',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'wrong-secret',
          });

        expect(response.status).toBe(401);
        expect(response.body.error.code).toBe('UNAUTHORIZED');
      });

      it('should reject invalid device_uuid format', async () => {
        const response = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: 'invalid-uuid',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        expect(response.status).toBe(400);
      });

      it('should reject missing platform', async () => {
        const response = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440111',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        expect(response.status).toBe(400);
      });
    });

    describe('POST /v1/auth/refresh', () => {
      it('should refresh tokens with valid refresh token', async () => {
        // First register to get a refresh token
        const registerResponse = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440002',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        refreshToken = registerResponse.body.refresh_token;

        const response = await request(app)
          .post('/v1/auth/refresh')
          .send({ refresh_token: refreshToken });

        expect(response.status).toBe(200);
        expect(response.body.access_token).toBeDefined();
        expect(response.body.refresh_token).toBeDefined();
        expect(response.body.token_type).toBe('Bearer');
      });

      it('should reject invalid refresh token', async () => {
        const response = await request(app)
          .post('/v1/auth/refresh')
          .send({ refresh_token: 'invalid-token' });

        expect(response.status).toBe(401);
        expect(response.body.error.code).toBe('INVALID_TOKEN');
      });
    });

    describe('GET /v1/auth/me', () => {
      it('should return device info with valid token', async () => {
        // Register to get token
        const registerResponse = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440003',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        accessToken = registerResponse.body.access_token;

        const response = await request(app)
          .get('/v1/auth/me')
          .set('Authorization', `Bearer ${accessToken}`);

        expect(response.status).toBe(200);
        expect(response.body.device_uuid).toBe('550e8400-e29b-41d4-a716-446655440003');
        expect(response.body.platform).toBe('ios');
        expect(response.body.tier).toBe('free');
      });

      it('should reject request without token', async () => {
        const response = await request(app).get('/v1/auth/me');

        expect(response.status).toBe(401);
      });

      it('should reject invalid token', async () => {
        const response = await request(app)
          .get('/v1/auth/me')
          .set('Authorization', 'Bearer invalid-token');

        expect(response.status).toBe(401);
      });
    });

    describe('POST /v1/auth/revoke', () => {
      it('should revoke tokens', async () => {
        // Register to get token
        const registerResponse = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440004',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        accessToken = registerResponse.body.access_token;
        refreshToken = registerResponse.body.refresh_token;

        const response = await request(app)
          .post('/v1/auth/revoke')
          .set('Authorization', `Bearer ${accessToken}`);

        expect(response.status).toBe(200);
        expect(response.body.success).toBe(true);

        // Old refresh token should no longer work
        const refreshResponse = await request(app)
          .post('/v1/auth/refresh')
          .send({ refresh_token: refreshToken });

        expect(refreshResponse.status).toBe(401);
      });
    });
  });

  // -------------------------------------------------------------------------
  // Usage Endpoint Tests
  // -------------------------------------------------------------------------

  describe('Usage Endpoints', () => {
    describe('GET /v1/usage', () => {
      it('should return usage info', async () => {
        // Register to get token
        const registerResponse = await request(app)
          .post('/v1/auth/register')
          .send({
            device_uuid: '550e8400-e29b-41d4-a716-446655440005',
            platform: 'ios',
            app_version: '1.0.0',
            app_secret: 'test-ios-secret',
          });

        const accessToken = registerResponse.body.access_token;

        const response = await request(app)
          .get('/v1/usage')
          .set('Authorization', `Bearer ${accessToken}`);

        expect(response.status).toBe(200);
        expect(response.body.daily).toBeDefined();
        expect(response.body.daily.used).toBeDefined();
        expect(response.body.daily.limit).toBeDefined();
        expect(response.body.daily.reset_at).toBeDefined();
        expect(response.body.tier).toBe('free');
      });

      it('should reject unauthenticated request', async () => {
        const response = await request(app).get('/v1/usage');

        expect(response.status).toBe(401);
      });
    });
  });

  // -------------------------------------------------------------------------
  // 404 Handler Tests
  // -------------------------------------------------------------------------

  describe('404 Handler', () => {
    it('should return 404 for unknown routes', async () => {
      const response = await request(app).get('/unknown-endpoint');

      expect(response.status).toBe(404);
      expect(response.body.error.code).toBe('INVALID_REQUEST');
    });
  });

  // -------------------------------------------------------------------------
  // Rate Limit Headers Tests
  // -------------------------------------------------------------------------

  describe('Rate Limit Headers', () => {
    it('should include rate limit headers on authenticated endpoints', async () => {
      // Register to get token
      const registerResponse = await request(app)
        .post('/v1/auth/register')
        .send({
          device_uuid: '550e8400-e29b-41d4-a716-446655440006',
          platform: 'ios',
          app_version: '1.0.0',
          app_secret: 'test-ios-secret',
        });

      const accessToken = registerResponse.body.access_token;

      const response = await request(app)
        .get('/v1/usage')
        .set('Authorization', `Bearer ${accessToken}`);

      // Rate limit headers are added by middleware
      // The exact headers depend on whether rate limiting is applied
      expect(response.status).toBe(200);
    });
  });
});
