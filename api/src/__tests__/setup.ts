// ============================================================================
// Jest Test Setup
// Mocks and configuration for all tests
// ============================================================================

// Export to make this a module (required for declare global)
export {};

// Set test environment variables
process.env.NODE_ENV = 'test';
process.env.JWT_SECRET = 'test-secret-key-at-least-32-chars-long';
process.env.JWT_ISSUER = 'reefscan-api-test';
process.env.APP_SECRET_IOS_V1 = 'test-ios-secret';
process.env.APP_SECRET_ANDROID_V1 = 'test-android-secret';
process.env.GEMINI_KEY_1 = 'test-gemini-key-1';
process.env.GEMINI_KEY_2 = 'test-gemini-key-2';
process.env.OPENAI_API_KEY = 'test-openai-key';
process.env.UPSTASH_REDIS_REST_URL = 'https://test.upstash.io';
process.env.UPSTASH_REDIS_REST_TOKEN = 'test-redis-token';

// -----------------------------------------------------------------------------
// Redis Mock
// -----------------------------------------------------------------------------

const mockRedisStore: Map<string, { value: unknown; expiry?: number }> = new Map();

jest.mock('../services/redis.service', () => ({
  get: jest.fn(async <T>(key: string): Promise<T | null> => {
    const item = mockRedisStore.get(key);
    if (!item) return null;
    if (item.expiry && Date.now() > item.expiry) {
      mockRedisStore.delete(key);
      return null;
    }
    return item.value as T;
  }),

  set: jest.fn(async (key: string, value: unknown, ttlSeconds?: number): Promise<boolean> => {
    mockRedisStore.set(key, {
      value,
      expiry: ttlSeconds ? Date.now() + ttlSeconds * 1000 : undefined,
    });
    return true;
  }),

  incr: jest.fn(async (key: string): Promise<number> => {
    const item = mockRedisStore.get(key);
    const newValue = ((item?.value as number) || 0) + 1;
    mockRedisStore.set(key, { value: newValue, expiry: item?.expiry });
    return newValue;
  }),

  expire: jest.fn(async (key: string, seconds: number): Promise<boolean> => {
    const item = mockRedisStore.get(key);
    if (item) {
      item.expiry = Date.now() + seconds * 1000;
    }
    return true;
  }),

  del: jest.fn(async (key: string): Promise<boolean> => {
    return mockRedisStore.delete(key);
  }),

  exists: jest.fn(async (key: string): Promise<boolean> => {
    return mockRedisStore.has(key);
  }),

  ttl: jest.fn(async (key: string): Promise<number> => {
    const item = mockRedisStore.get(key);
    if (!item || !item.expiry) return -1;
    return Math.floor((item.expiry - Date.now()) / 1000);
  }),

  testConnection: jest.fn(async (): Promise<boolean> => true),

  // Export for test manipulation
  __mockStore: mockRedisStore,
  __clearStore: () => mockRedisStore.clear(),
}));

// -----------------------------------------------------------------------------
// Database Mock
// -----------------------------------------------------------------------------

const mockDevices: Map<string, Record<string, unknown>> = new Map();

jest.mock('../db', () => ({
  sql: Object.assign(
    jest.fn(async (strings: TemplateStringsArray, ...values: unknown[]) => {
      // Parse SQL query to determine operation
      const query = strings.join('?').toLowerCase();

      if (query.includes('select 1')) {
        return { rows: [{ '?column?': 1 }] };
      }

      if (query.includes('insert into devices')) {
        const id = values[0] as string;
        const device = {
          id,
          device_uuid: values[1],
          platform: values[2],
          app_version: values[3],
          tier: values[4] || 'free',
          token_version: values[5] || 1,
          is_blocked: values[6] || false,
          metadata: values[7] || {},
          created_at: new Date().toISOString(),
          last_seen_at: new Date().toISOString(),
          refresh_token: null,
          subscription_id: null,
          block_reason: null,
        };
        mockDevices.set(id, device);
        mockDevices.set(`uuid:${values[1]}`, device);
        return { rows: [device], rowCount: 1 };
      }

      if (query.includes('select * from devices where device_uuid')) {
        const device = mockDevices.get(`uuid:${values[0]}`);
        return { rows: device ? [device] : [], rowCount: device ? 1 : 0 };
      }

      if (query.includes('select * from devices where id')) {
        const device = mockDevices.get(values[0] as string);
        return { rows: device ? [device] : [], rowCount: device ? 1 : 0 };
      }

      if (query.includes('update devices')) {
        const deviceUuid = values[values.length - 1];
        const device = mockDevices.get(`uuid:${deviceUuid}`);
        if (device) {
          if (query.includes('token_version = token_version + 1')) {
            device.token_version = (device.token_version as number) + 1;
          }
          if (query.includes('last_seen_at')) {
            device.last_seen_at = new Date().toISOString();
          }
          return { rows: [device], rowCount: 1 };
        }
        return { rows: [], rowCount: 0 };
      }

      if (query.includes('delete from devices')) {
        const deviceUuid = values[0];
        const device = mockDevices.get(`uuid:${deviceUuid}`);
        if (device) {
          mockDevices.delete(device.id as string);
          mockDevices.delete(`uuid:${deviceUuid}`);
          return { rowCount: 1 };
        }
        return { rowCount: 0 };
      }

      // Default: return empty result
      return { rows: [], rowCount: 0 };
    }),
    {
      query: jest.fn(async () => ({ rows: [] })),
    }
  ),
  testConnection: jest.fn(async () => true),
  query: jest.fn(async () => []),

  // Export for test manipulation
  __mockDevices: mockDevices,
  __clearDevices: () => mockDevices.clear(),
}));

// -----------------------------------------------------------------------------
// Global Test Utilities
// -----------------------------------------------------------------------------

// Clear all mocks before each test
beforeEach(() => {
  jest.clearAllMocks();
  mockRedisStore.clear();
  mockDevices.clear();
});

// Global test utilities
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace jest {
    interface Matchers<R> {
      toBeWithinRange(floor: number, ceiling: number): R;
    }
  }
}

expect.extend({
  toBeWithinRange(received: number, floor: number, ceiling: number) {
    const pass = received >= floor && received <= ceiling;
    if (pass) {
      return {
        message: () =>
          `expected ${received} not to be within range ${floor} - ${ceiling}`,
        pass: true,
      };
    } else {
      return {
        message: () =>
          `expected ${received} to be within range ${floor} - ${ceiling}`,
        pass: false,
      };
    }
  },
});
