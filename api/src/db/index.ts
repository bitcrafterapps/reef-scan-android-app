// ============================================================================
// Database Connection & Utilities
// Uses standard pg for local development, compatible with @vercel/postgres API
// ============================================================================

import { Pool, QueryResult, QueryResultRow } from 'pg';

// Create a connection pool with SSL for Vercel Postgres
const pool = new Pool({
  connectionString: process.env.POSTGRES_URL,
  ssl: process.env.NODE_ENV === 'production'
    ? { rejectUnauthorized: false }
    : undefined,
});

// Type for SQL template tag result
interface SqlResult<T extends QueryResultRow = QueryResultRow> {
  rows: T[];
  rowCount: number | null;
}

/**
 * SQL template tag function - compatible with @vercel/postgres API
 * Usage: sql`SELECT * FROM users WHERE id = ${userId}`
 */
export async function sql<T extends QueryResultRow = QueryResultRow>(
  strings: TemplateStringsArray,
  ...values: unknown[]
): Promise<SqlResult<T>> {
  // Build the query with numbered placeholders ($1, $2, etc.)
  let query = '';
  for (let i = 0; i < strings.length; i++) {
    query += strings[i];
    if (i < values.length) {
      query += `$${i + 1}`;
    }
  }

  const result = await pool.query(query, values);
  return {
    rows: result.rows as T[],
    rowCount: result.rowCount,
  };
}

// Add query method to sql function for compatibility
sql.query = async function<T extends QueryResultRow = QueryResultRow>(
  queryText: string,
  values?: unknown[]
): Promise<QueryResult<T>> {
  return pool.query(queryText, values);
};

/**
 * Test database connection
 */
export async function testConnection(): Promise<boolean> {
  try {
    await pool.query('SELECT 1');
    return true;
  } catch (error) {
    console.error('Database connection failed:', error);
    return false;
  }
}

/**
 * Execute raw SQL query
 */
export async function query<T>(
  queryText: string,
  values?: unknown[]
): Promise<T[]> {
  try {
    const result = await pool.query(queryText, values);
    return result.rows as T[];
  } catch (error) {
    console.error('Query failed:', queryText, error);
    throw error;
  }
}

/**
 * Get pool for direct access if needed
 */
export { pool };

// Re-export types for compatibility
export type { QueryResult, QueryResultRow };
