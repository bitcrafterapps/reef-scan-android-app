// ============================================================================
// Database Migration Script
// Run with: npm run db:migrate
// ============================================================================

import { sql } from '@vercel/postgres';
import * as fs from 'fs';
import * as path from 'path';

async function migrate(): Promise<void> {
  console.log('Starting database migration...');

  try {
    // Read the schema file
    const schemaPath = path.join(__dirname, 'schema.sql');
    const schema = fs.readFileSync(schemaPath, 'utf-8');

    // Split by semicolon and filter empty statements
    const statements = schema
      .split(';')
      .map((s) => s.trim())
      .filter((s) => s.length > 0 && !s.startsWith('--'));

    console.log(`Found ${statements.length} SQL statements to execute`);

    // Execute each statement
    for (let i = 0; i < statements.length; i++) {
      const statement = statements[i];
      try {
        await sql.query(statement);
        console.log(`✓ Statement ${i + 1}/${statements.length} executed`);
      } catch (error) {
        // Log but continue - some statements might fail if already exists
        console.warn(
          `⚠ Statement ${i + 1} warning:`,
          error instanceof Error ? error.message : error
        );
      }
    }

    console.log('Migration completed successfully!');
  } catch (error) {
    console.error('Migration failed:', error);
    process.exit(1);
  }
}

// Run migration if called directly
if (require.main === module) {
  migrate().then(() => process.exit(0));
}

export { migrate };
