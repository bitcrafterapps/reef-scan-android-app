// ============================================================================
// OpenAI Service (Fallback)
// Handles image analysis using OpenAI's GPT-4o Vision API as fallback
// ============================================================================

import OpenAI from 'openai';
import config from '../config';
import * as redis from './redis.service';
import type { AnalysisMode, ScanResult, Identification } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// OpenAI Client
// -----------------------------------------------------------------------------

let openaiClient: OpenAI | null = null;

function getClient(): OpenAI {
  if (!openaiClient) {
    if (!config.openai.apiKey) {
      throw new OpenAIError('NOT_CONFIGURED', 'OpenAI API key not configured');
    }
    openaiClient = new OpenAI({
      apiKey: config.openai.apiKey,
    });
  }
  return openaiClient;
}

// -----------------------------------------------------------------------------
// Types
// -----------------------------------------------------------------------------

interface AnalysisResult {
  success: boolean;
  result?: ScanResult;
  tokensUsed?: {
    input: number;
    output: number;
  };
  error?: {
    code: string;
    message: string;
  };
  cost?: number;
}

// -----------------------------------------------------------------------------
// Cost Tracking
// -----------------------------------------------------------------------------

const REDIS_KEY_DAILY_COST = 'openai:daily_cost';

// GPT-4o pricing (approximate)
const COST_PER_INPUT_TOKEN = 0.005 / 1000;  // $5 per 1M input tokens
const COST_PER_OUTPUT_TOKEN = 0.015 / 1000; // $15 per 1M output tokens

async function getDailyCost(): Promise<number> {
  return await redis.get<number>(REDIS_KEY_DAILY_COST) || 0;
}

async function addToDailyCost(cost: number): Promise<void> {
  const currentCost = await getDailyCost();
  const newCost = currentCost + cost;

  // Set with expiry until midnight UTC
  const now = new Date();
  const midnight = new Date(Date.UTC(
    now.getUTCFullYear(),
    now.getUTCMonth(),
    now.getUTCDate() + 1,
    0, 0, 0, 0
  ));
  const secondsUntilMidnight = Math.floor((midnight.getTime() - now.getTime()) / 1000);

  await redis.set(REDIS_KEY_DAILY_COST, newCost, secondsUntilMidnight);
}

function calculateCost(inputTokens: number, outputTokens: number): number {
  return (inputTokens * COST_PER_INPUT_TOKEN) + (outputTokens * COST_PER_OUTPUT_TOKEN);
}

// -----------------------------------------------------------------------------
// Prompts
// -----------------------------------------------------------------------------

const SYSTEM_PROMPT = `You are ReefScan, an expert marine aquarium analyst AI. You analyze images of reef tanks to identify fish, coral, invertebrates, algae, and potential problems.

Your analysis should be:
- Accurate and specific (identify species when possible)
- Practical and actionable
- Focused on tank health and livestock welfare

Always respond with valid JSON matching the requested schema.`;

const MODE_PROMPTS: Record<AnalysisMode, string> = {
  comprehensive: `Analyze this reef tank image comprehensively. Identify all visible fish, coral, invertebrates, and any potential problems like algae, pests, or health issues. Assess overall tank health.`,

  fish_id: `Focus on identifying all fish visible in this reef tank image. Provide species names, health assessment, and any concerns about compatibility or behavior.`,

  coral_id: `Focus on identifying all coral visible in this reef tank image. Provide species/type names, health assessment, and any signs of stress, bleaching, or disease.`,

  algae_id: `Focus on identifying any algae visible in this reef tank image. Determine if it's beneficial or problematic, identify the type, and suggest remediation if needed.`,

  pest_id: `Focus on identifying any pests or parasites visible in this reef tank image. Look for aiptasia, flatworms, bristleworms, red bugs, or any other common aquarium pests.`,
};

const RESPONSE_SCHEMA = `
Respond with JSON in this exact format:
{
  "tank_health": "Excellent" | "Good" | "Fair" | "Needs Attention" | "Critical",
  "summary": "Brief 1-2 sentence summary of the tank",
  "identifications": [
    {
      "name": "Species or item name",
      "category": "fish" | "coral" | "invertebrate" | "algae" | "pest" | "equipment" | "other",
      "confidence": 0.0-1.0,
      "is_problem": boolean,
      "severity": "low" | "medium" | "high" | null,
      "description": "Brief description and any concerns"
    }
  ],
  "recommendations": ["List of actionable recommendations"]
}`;

// -----------------------------------------------------------------------------
// Main Analysis Function
// -----------------------------------------------------------------------------

/**
 * Analyze an image using OpenAI Vision API (fallback)
 */
export async function analyzeImageFallback(
  imageData: string,
  mimeType: 'image/jpeg' | 'image/png',
  mode: AnalysisMode
): Promise<AnalysisResult> {
  const startTime = Date.now();

  // Check if OpenAI is enabled
  if (!config.features.enableOpenAiFallback) {
    return {
      success: false,
      error: {
        code: 'DISABLED',
        message: 'OpenAI fallback is disabled',
      },
    };
  }

  // Check daily cost limit
  const dailyCost = await getDailyCost();
  if (dailyCost >= config.openai.maxCostPerDay) {
    logger.warn('OpenAI daily cost limit reached', {
      current_cost: dailyCost,
      limit: config.openai.maxCostPerDay,
    });

    return {
      success: false,
      error: {
        code: 'COST_LIMIT',
        message: 'Daily cost limit reached for OpenAI fallback',
      },
    };
  }

  try {
    const client = getClient();
    const prompt = `${MODE_PROMPTS[mode]}\n\n${RESPONSE_SCHEMA}`;

    const response = await client.chat.completions.create({
      model: config.openai.model,
      messages: [
        {
          role: 'system',
          content: SYSTEM_PROMPT,
        },
        {
          role: 'user',
          content: [
            {
              type: 'text',
              text: prompt,
            },
            {
              type: 'image_url',
              image_url: {
                url: `data:${mimeType};base64,${imageData}`,
                detail: 'high',
              },
            },
          ],
        },
      ],
      max_tokens: 2048,
      temperature: 0.2,
      response_format: { type: 'json_object' },
    });

    const latencyMs = Date.now() - startTime;
    const inputTokens = response.usage?.prompt_tokens || 0;
    const outputTokens = response.usage?.completion_tokens || 0;
    const cost = calculateCost(inputTokens, outputTokens);

    // Track cost
    await addToDailyCost(cost);

    // Parse response
    const content = response.choices[0]?.message?.content;
    if (!content) {
      throw new OpenAIError('INVALID_RESPONSE', 'No content in response');
    }

    const result = parseResponse(content);

    logger.info('OpenAI fallback analysis completed', {
      mode,
      latency_ms: latencyMs,
      tokens_input: inputTokens,
      tokens_output: outputTokens,
      cost_usd: cost.toFixed(4),
    });

    return {
      success: true,
      result,
      tokensUsed: {
        input: inputTokens,
        output: outputTokens,
      },
      cost,
    };
  } catch (error) {
    const latencyMs = Date.now() - startTime;

    logger.error('OpenAI fallback analysis failed', {
      mode,
      latency_ms: latencyMs,
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    if (error instanceof OpenAIError) {
      return {
        success: false,
        error: {
          code: error.code,
          message: error.message,
        },
      };
    }

    return {
      success: false,
      error: {
        code: 'OPENAI_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
      },
    };
  }
}

// -----------------------------------------------------------------------------
// Response Parsing
// -----------------------------------------------------------------------------

function parseResponse(content: string): ScanResult {
  try {
    const parsed = JSON.parse(content) as {
      tank_health?: string;
      summary?: string;
      identifications?: Identification[];
      recommendations?: string[];
    };

    return {
      request_id: '', // Will be set by caller
      tank_health: validateTankHealth(parsed.tank_health),
      summary: parsed.summary || 'Analysis complete',
      identifications: normalizeIdentifications(parsed.identifications || []),
      recommendations: parsed.recommendations || [],
      usage: {
        requests_today: 0, // Will be set by caller
        daily_limit: 0,
        reset_at: '',
      },
    };
  } catch (parseError) {
    logger.error('Failed to parse OpenAI response', {
      response_text: content.substring(0, 500),
      error: parseError instanceof Error ? parseError.message : 'Parse error',
    });

    throw new OpenAIError('PARSE_ERROR', 'Failed to parse AI response');
  }
}

function validateTankHealth(health: string | undefined): ScanResult['tank_health'] {
  const validValues = ['Excellent', 'Good', 'Fair', 'Needs Attention', 'Critical'] as const;
  if (health && validValues.includes(health as typeof validValues[number])) {
    return health as ScanResult['tank_health'];
  }
  return 'Good';
}

function normalizeIdentifications(identifications: Identification[]): Identification[] {
  return identifications.map((id) => ({
    name: id.name || 'Unknown',
    category: id.category || 'other',
    confidence: Math.min(1, Math.max(0, id.confidence || 0.5)),
    is_problem: Boolean(id.is_problem),
    severity: id.is_problem ? (id.severity || 'low') : null,
    description: id.description || '',
  }));
}

// -----------------------------------------------------------------------------
// Utility Functions
// -----------------------------------------------------------------------------

/**
 * Check if OpenAI fallback is available
 */
export async function isAvailable(): Promise<boolean> {
  if (!config.features.enableOpenAiFallback || !config.openai.apiKey) {
    return false;
  }

  const dailyCost = await getDailyCost();
  return dailyCost < config.openai.maxCostPerDay;
}

/**
 * Get current daily cost
 */
export async function getCurrentDailyCost(): Promise<number> {
  return getDailyCost();
}

// -----------------------------------------------------------------------------
// Error Class
// -----------------------------------------------------------------------------

export class OpenAIError extends Error {
  code: string;

  constructor(code: string, message: string) {
    super(message);
    this.code = code;
    this.name = 'OpenAIError';
  }
}
