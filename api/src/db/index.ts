// ============================================================================
// Database Connection & Utilities
// Uses Vercel Postgres (@vercel/postgres)
// ============================================================================

import { sql } from '@vercel/postgres';

/**
 * Test database connection
 */
export async function testConnection(): Promise<boolean> {
  try {
    await sql`SELECT 1`;
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
    const result = await sql.query(queryText, values);
    return result.rows as T[];
  } catch (error) {
    console.error('Query failed:', queryText, error);
    throw error;
  }
}

/**
 * Database client for transactions
 */
export { sql };

// Re-export types
export type { QueryResult, QueryResultRow } from '@vercel/postgres';
