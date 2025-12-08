// ============================================================================
// Prompt Management Service
// Manages AI prompts with versioning and caching
// ============================================================================

import { sql } from '../db';
import * as redis from './redis.service';
import type { Prompt, AnalysisMode } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Redis Key Patterns
// -----------------------------------------------------------------------------

const KEYS = {
  promptCache: (name: string) => `prompt:${name}`,
};

// Cache TTL in seconds (5 minutes)
const CACHE_TTL = 300;

// -----------------------------------------------------------------------------
// Default Prompts
// -----------------------------------------------------------------------------

const DEFAULT_SYSTEM_PROMPT = `You are ReefScan, an expert marine aquarium analyst AI. You analyze images of reef tanks to identify fish, coral, invertebrates, algae, and potential problems.

Your analysis should be:
- Accurate and specific (identify species when possible)
- Practical and actionable
- Focused on tank health and livestock welfare

Always respond with valid JSON matching the requested schema.`;

const DEFAULT_MODE_PROMPTS: Record<AnalysisMode, string> = {
  comprehensive: `Analyze this reef tank image comprehensively. Identify all visible fish, coral, invertebrates, and any potential problems like algae, pests, or health issues. Assess overall tank health.`,

  fish_id: `Focus on identifying all fish visible in this reef tank image. Provide species names, health assessment, and any concerns about compatibility or behavior.`,

  coral_id: `Focus on identifying all coral visible in this reef tank image. Provide species/type names, health assessment, and any signs of stress, bleaching, or disease.`,

  algae_id: `Focus on identifying any algae visible in this reef tank image. Determine if it's beneficial or problematic, identify the type, and suggest remediation if needed.`,

  pest_id: `Focus on identifying any pests or parasites visible in this reef tank image. Look for aiptasia, flatworms, bristleworms, red bugs, or any other common aquarium pests.`,
};

// -----------------------------------------------------------------------------
// Prompt Retrieval
// -----------------------------------------------------------------------------

/**
 * Get active prompt for a mode
 */
export async function getActivePrompt(mode: AnalysisMode): Promise<{
  systemPrompt: string;
  modePrompt: string;
  version: number;
}> {
  // Check cache first
  const cacheKey = KEYS.promptCache(mode);
  const cached = await redis.get<{
    systemPrompt: string;
    modePrompt: string;
    version: number;
  }>(cacheKey);

  if (cached) {
    return cached;
  }

  // Try to get from database
  try {
    const result = await sql`
      SELECT system_prompt, mode_prompt, version
      FROM prompts
      WHERE name = ${mode}
        AND is_active = true
      ORDER BY version DESC
      LIMIT 1
    `;

    if (result.rows.length > 0) {
      const prompt = {
        systemPrompt: result.rows[0].system_prompt as string,
        modePrompt: result.rows[0].mode_prompt as string,
        version: result.rows[0].version as number,
      };

      // Cache the result
      await redis.set(cacheKey, prompt, CACHE_TTL);

      return prompt;
    }
  } catch (error) {
    logger.error('Failed to get prompt from database', {
      mode,
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }

  // Fall back to defaults
  const defaultPrompt = {
    systemPrompt: DEFAULT_SYSTEM_PROMPT,
    modePrompt: DEFAULT_MODE_PROMPTS[mode],
    version: 0,
  };

  // Cache the default
  await redis.set(cacheKey, defaultPrompt, CACHE_TTL);

  return defaultPrompt;
}

/**
 * Get all active prompts
 */
export async function getAllActivePrompts(): Promise<Map<AnalysisMode, Prompt>> {
  const prompts = new Map<AnalysisMode, Prompt>();
  const modes: AnalysisMode[] = ['comprehensive', 'fish_id', 'coral_id', 'algae_id', 'pest_id'];

  try {
    const result = await sql`
      SELECT DISTINCT ON (name) *
      FROM prompts
      WHERE is_active = true
      ORDER BY name, version DESC
    `;

    for (const row of result.rows) {
      prompts.set(row.name as AnalysisMode, {
        id: row.id as number,
        name: row.name as string,
        version: row.version as number,
        system_prompt: row.system_prompt as string,
        mode_prompt: row.mode_prompt as string,
        is_active: row.is_active as boolean,
        created_at: new Date(row.created_at as string),
        created_by: row.created_by as string | null,
      });
    }
  } catch (error) {
    logger.error('Failed to get prompts from database', {
      error: error instanceof Error ? error.message : 'Unknown error',
    });
  }

  // Fill in defaults for missing modes
  for (const mode of modes) {
    if (!prompts.has(mode)) {
      prompts.set(mode, {
        id: 0,
        name: mode,
        version: 0,
        system_prompt: DEFAULT_SYSTEM_PROMPT,
        mode_prompt: DEFAULT_MODE_PROMPTS[mode],
        is_active: true,
        created_at: new Date(),
        created_by: null,
      });
    }
  }

  return prompts;
}

// -----------------------------------------------------------------------------
// Prompt Management (Admin)
// -----------------------------------------------------------------------------

/**
 * Create a new prompt version
 */
export async function createPromptVersion(
  name: AnalysisMode,
  systemPrompt: string,
  modePrompt: string,
  createdBy: string
): Promise<Prompt> {
  // Get current highest version
  const versionResult = await sql`
    SELECT COALESCE(MAX(version), 0) as max_version
    FROM prompts
    WHERE name = ${name}
  `;

  const newVersion = (versionResult.rows[0].max_version as number) + 1;

  // Insert new version (inactive by default)
  const result = await sql`
    INSERT INTO prompts (name, version, system_prompt, mode_prompt, is_active, created_by)
    VALUES (${name}, ${newVersion}, ${systemPrompt}, ${modePrompt}, false, ${createdBy})
    RETURNING *
  `;

  logger.info('Prompt version created', {
    name,
    version: newVersion,
    created_by: createdBy,
  });

  return {
    id: result.rows[0].id as number,
    name: result.rows[0].name as string,
    version: result.rows[0].version as number,
    system_prompt: result.rows[0].system_prompt as string,
    mode_prompt: result.rows[0].mode_prompt as string,
    is_active: result.rows[0].is_active as boolean,
    created_at: new Date(result.rows[0].created_at as string),
    created_by: result.rows[0].created_by as string | null,
  };
}

/**
 * Activate a specific prompt version
 */
export async function activatePromptVersion(
  name: AnalysisMode,
  version: number
): Promise<void> {
  // Deactivate all versions
  await sql`
    UPDATE prompts
    SET is_active = false
    WHERE name = ${name}
  `;

  // Activate the specified version
  await sql`
    UPDATE prompts
    SET is_active = true
    WHERE name = ${name} AND version = ${version}
  `;

  // Clear cache
  await redis.del(KEYS.promptCache(name));

  logger.info('Prompt version activated', {
    name,
    version,
  });
}

/**
 * Get all versions of a prompt
 */
export async function getPromptVersions(name: AnalysisMode): Promise<Prompt[]> {
  const result = await sql`
    SELECT *
    FROM prompts
    WHERE name = ${name}
    ORDER BY version DESC
  `;

  return result.rows.map((row) => ({
    id: row.id as number,
    name: row.name as string,
    version: row.version as number,
    system_prompt: row.system_prompt as string,
    mode_prompt: row.mode_prompt as string,
    is_active: row.is_active as boolean,
    created_at: new Date(row.created_at as string),
    created_by: row.created_by as string | null,
  }));
}

// -----------------------------------------------------------------------------
// Cache Management
// -----------------------------------------------------------------------------

/**
 * Clear all prompt caches
 */
export async function clearPromptCache(): Promise<void> {
  const modes: AnalysisMode[] = ['comprehensive', 'fish_id', 'coral_id', 'algae_id', 'pest_id'];

  for (const mode of modes) {
    await redis.del(KEYS.promptCache(mode));
  }

  logger.info('Prompt cache cleared');
}
