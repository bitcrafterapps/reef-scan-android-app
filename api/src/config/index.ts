// ============================================================================
// Application Configuration
// Centralized configuration management
// ============================================================================

// -----------------------------------------------------------------------------
// Environment Variables Validation
// -----------------------------------------------------------------------------

function requireEnv(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

function optionalEnv(name: string, defaultValue: string): string {
  return process.env[name] || defaultValue;
}

// -----------------------------------------------------------------------------
// Configuration Object
// -----------------------------------------------------------------------------

export const config = {
  // Environment
  env: optionalEnv('NODE_ENV', 'development'),
  isProduction: process.env.NODE_ENV === 'production',
  isDevelopment: process.env.NODE_ENV === 'development',

  // Server
  port: parseInt(optionalEnv('PORT', '3000'), 10),

  // JWT Configuration
  jwt: {
    secret: requireEnv('JWT_SECRET'),
    issuer: optionalEnv('JWT_ISSUER', 'reefscan-api'),
    accessTokenExpirySeconds: 3600, // 1 hour
    refreshTokenExpirySeconds: 30 * 24 * 60 * 60, // 30 days
  },

  // App Secrets (for validating legitimate app installs)
  appSecrets: {
    ios: {
      v1: process.env.APP_SECRET_IOS_V1 || '',
    },
    android: {
      v1: process.env.APP_SECRET_ANDROID_V1 || '',
    },
  },

  // Gemini API Configuration
  gemini: {
    keys: [
      process.env.GEMINI_KEY_1,
      process.env.GEMINI_KEY_2,
      process.env.GEMINI_KEY_3,
    ].filter(Boolean) as string[],
    model: optionalEnv('GEMINI_MODEL', 'gemini-2.0-flash'),
    baseUrl: 'https://generativelanguage.googleapis.com/v1beta',
  },

  // OpenAI Fallback Configuration
  openai: {
    apiKey: process.env.OPENAI_API_KEY || '',
    model: optionalEnv('OPENAI_MODEL', 'gpt-4o'),
    maxCostPerDay: parseFloat(optionalEnv('OPENAI_MAX_COST_PER_DAY', '100')),
  },

  // Rate Limiting
  rateLimit: {
    free: {
      daily: 3,
      perMinute: 5,
    },
    premium: {
      daily: 20,
      perMinute: 5,
    },
    global: {
      rpm: 500,
    },
    ipLimit: {
      perHour: 100,
    },
  },

  // Circuit Breaker
  circuitBreaker: {
    gemini: {
      failureThreshold: 5,
      successThreshold: 3,
      timeoutSeconds: 30,
      halfOpenRequests: 3,
    },
    openai: {
      failureThreshold: 5,
      successThreshold: 3,
      timeoutSeconds: 60,
      halfOpenRequests: 3,
    },
  },

  // Caching
  cache: {
    imageTtlDays: 7,
    idempotencyTtlHours: 24,
    jwtValidationTtlMinutes: 5,
  },

  // Request Limits
  limits: {
    maxImageSizeMB: 5,
    maxRequestSizeMB: 10,
    requestTimeoutMs: 30000,
  },

  // Feature Flags
  features: {
    enableOpenAiFallback: optionalEnv('ENABLE_OPENAI_FALLBACK', 'true') === 'true',
    enableImageCaching: optionalEnv('ENABLE_IMAGE_CACHING', 'true') === 'true',
  },

  // API Version
  apiVersion: 'v1',
  appVersion: '1.0.0',
};

// -----------------------------------------------------------------------------
// Validation
// -----------------------------------------------------------------------------

export function validateConfig(): void {
  const errors: string[] = [];

  // Check required in production
  if (config.isProduction) {
    if (!config.jwt.secret || config.jwt.secret.length < 32) {
      errors.push('JWT_SECRET must be at least 32 characters in production');
    }

    if (config.gemini.keys.length === 0) {
      errors.push('At least one GEMINI_KEY is required');
    }

    if (!config.appSecrets.ios.v1 && !config.appSecrets.android.v1) {
      errors.push('At least one APP_SECRET is required');
    }
  }

  if (errors.length > 0) {
    console.error('Configuration errors:');
    errors.forEach((err) => console.error(`  - ${err}`));
    throw new Error('Invalid configuration');
  }
}

// Export default
export default config;
