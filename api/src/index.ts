// ============================================================================
// ReefScan API Gateway
// Main Express Application Entry Point
// ============================================================================

import express, { Request, Response, NextFunction } from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { v4 as uuidv4 } from 'uuid';

import config, { validateConfig } from './config';
import { testConnection as testDbConnection } from './db';
import { testConnection as testRedisConnection } from './services/redis.service';
import type { HealthStatus } from './types';

// Route imports
import authRoutes from './routes/auth.routes';
import usageRoutes from './routes/usage.routes';
import analyzeRoutes from './routes/analyze.routes';
import accountRoutes from './routes/account.routes';

// Middleware imports
import { enforceIpRateLimit } from './middleware/rateLimit.middleware';
import { errorHandler, notFoundHandler, timeoutHandler } from './middleware/errorHandler.middleware';

// -----------------------------------------------------------------------------
// Initialize Express App
// -----------------------------------------------------------------------------

const app = express();

// -----------------------------------------------------------------------------
// Middleware
// -----------------------------------------------------------------------------

// Security headers
app.use(helmet());

// CORS configuration for mobile apps
app.use(
  cors({
    origin: '*', // Allow all origins for mobile apps
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: [
      'Content-Type',
      'Authorization',
      'X-Request-ID',
      'X-App-Version',
    ],
    exposedHeaders: [
      'X-RateLimit-Limit',
      'X-RateLimit-Remaining',
      'X-RateLimit-Reset',
      'X-RateLimit-Window',
      'X-RateLimit-Tier',
    ],
  })
);

// Body parsing with size limits
app.use(express.json({ limit: `${config.limits.maxRequestSizeMB}mb` }));
app.use(express.urlencoded({ extended: true, limit: `${config.limits.maxRequestSizeMB}mb` }));

// Request ID middleware
app.use((req: Request, _res: Response, next: NextFunction) => {
  req.requestId = (req.headers['x-request-id'] as string) || uuidv4();
  next();
});

// Request logging middleware
app.use((req: Request, res: Response, next: NextFunction) => {
  const start = Date.now();

  res.on('finish', () => {
    const duration = Date.now() - start;
    console.log(
      JSON.stringify({
        timestamp: new Date().toISOString(),
        request_id: req.requestId,
        method: req.method,
        path: req.path,
        status: res.statusCode,
        duration_ms: duration,
        user_agent: req.headers['user-agent'],
      })
    );
  });

  next();
});

// IP-based rate limiting (abuse prevention)
app.use(enforceIpRateLimit);

// Request timeout (30 seconds)
app.use(timeoutHandler(config.limits.requestTimeoutMs));

// -----------------------------------------------------------------------------
// Health Check Endpoint
// -----------------------------------------------------------------------------

app.get('/health', async (_req: Request, res: Response) => {
  const [dbConnected, redisConnected] = await Promise.all([
    testDbConnection().catch(() => false),
    testRedisConnection().catch(() => false),
  ]);

  const status: HealthStatus = {
    status: dbConnected && redisConnected ? 'healthy' : 'degraded',
    version: config.appVersion,
    providers: {
      gemini: {
        status: config.gemini.keys.length > 0 ? 'operational' : 'disabled',
      },
      openai: {
        status: config.features.enableOpenAiFallback && config.openai.apiKey
          ? 'operational'
          : 'disabled',
      },
    },
    database: dbConnected ? 'connected' : 'disconnected',
    redis: redisConnected ? 'connected' : 'disconnected',
  };

  const httpStatus = status.status === 'healthy' ? 200 : 503;
  res.status(httpStatus).json(status);
});

// -----------------------------------------------------------------------------
// API Version Info
// -----------------------------------------------------------------------------

app.get('/', (_req: Request, res: Response) => {
  res.json({
    name: 'ReefScan API',
    version: config.appVersion,
    api_version: config.apiVersion,
    documentation: 'https://docs.reefscan.app/api',
  });
});

// -----------------------------------------------------------------------------
// API Routes
// -----------------------------------------------------------------------------

// Authentication routes (Phase 2)
app.use('/v1/auth', authRoutes);

// Usage routes (Phase 3)
app.use('/v1/usage', usageRoutes);

// Analysis routes (Phase 6)
app.use('/v1/analyze', analyzeRoutes);

// Account routes (GDPR compliance)
app.use('/v1/account', accountRoutes);

// -----------------------------------------------------------------------------
// 404 Handler
// -----------------------------------------------------------------------------

app.use(notFoundHandler);

// -----------------------------------------------------------------------------
// Error Handler
// -----------------------------------------------------------------------------

app.use(errorHandler);

// -----------------------------------------------------------------------------
// Server Startup
// -----------------------------------------------------------------------------

// Validate configuration on startup
try {
  validateConfig();
  console.log('Configuration validated successfully');
} catch (error) {
  console.error('Configuration validation failed:', error);
  // Don't exit in development, allow for partial functionality
  if (config.isProduction) {
    process.exit(1);
  }
}

// Start server (for local development)
if (require.main === module) {
  app.listen(config.port, () => {
    console.log(`ReefScan API running on port ${config.port}`);
    console.log(`Environment: ${config.env}`);
    console.log(`Health check: http://localhost:${config.port}/health`);
  });
}

// Export for Vercel serverless
export default app;
