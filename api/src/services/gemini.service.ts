// ============================================================================
// Gemini API Service
// Handles image analysis using Google's Gemini Vision API
// ============================================================================

import config from '../config';
import type { AnalysisMode, ScanResult, Identification } from '../types';
import logger from '../utils/logger';

// -----------------------------------------------------------------------------
// Types
// -----------------------------------------------------------------------------

interface GeminiRequest {
  contents: Array<{
    parts: Array<{
      text?: string;
      inline_data?: {
        mime_type: string;
        data: string;
      };
    }>;
  }>;
  generationConfig: {
    temperature: number;
    maxOutputTokens: number;
    responseMimeType: string;
  };
  safetySettings: Array<{
    category: string;
    threshold: string;
  }>;
}

interface GeminiResponse {
  candidates?: Array<{
    content?: {
      parts?: Array<{
        text?: string;
      }>;
    };
    finishReason?: string;
  }>;
  usageMetadata?: {
    promptTokenCount?: number;
    candidatesTokenCount?: number;
    totalTokenCount?: number;
  };
  error?: {
    code: number;
    message: string;
    status: string;
  };
}

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
    statusCode?: number;
  };
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
 * Analyze an image using Gemini Vision API
 */
export async function analyzeImage(
  imageData: string,
  mimeType: 'image/jpeg' | 'image/png',
  mode: AnalysisMode,
  apiKey: string
): Promise<AnalysisResult> {
  const startTime = Date.now();

  try {
    // Build the request
    const request = buildRequest(imageData, mimeType, mode);

    // Make the API call
    const response = await callGeminiApi(request, apiKey);

    // Parse the response
    const result = parseResponse(response, mode);

    const latencyMs = Date.now() - startTime;

    logger.debug('Gemini analysis completed', {
      mode,
      latency_ms: latencyMs,
      tokens_input: response.usageMetadata?.promptTokenCount,
      tokens_output: response.usageMetadata?.candidatesTokenCount,
    });

    return {
      success: true,
      result,
      tokensUsed: {
        input: response.usageMetadata?.promptTokenCount || 0,
        output: response.usageMetadata?.candidatesTokenCount || 0,
      },
    };
  } catch (error) {
    const latencyMs = Date.now() - startTime;

    logger.error('Gemini analysis failed', {
      mode,
      latency_ms: latencyMs,
      error: error instanceof Error ? error.message : 'Unknown error',
    });

    if (error instanceof GeminiError) {
      return {
        success: false,
        error: {
          code: error.code,
          message: error.message,
          statusCode: error.statusCode,
        },
      };
    }

    return {
      success: false,
      error: {
        code: 'GEMINI_ERROR',
        message: error instanceof Error ? error.message : 'Unknown error',
      },
    };
  }
}

// -----------------------------------------------------------------------------
// Request Building
// -----------------------------------------------------------------------------

function buildRequest(
  imageData: string,
  mimeType: string,
  mode: AnalysisMode
): GeminiRequest {
  const prompt = `${SYSTEM_PROMPT}\n\n${MODE_PROMPTS[mode]}\n\n${RESPONSE_SCHEMA}`;

  return {
    contents: [
      {
        parts: [
          {
            text: prompt,
          },
          {
            inline_data: {
              mime_type: mimeType,
              data: imageData,
            },
          },
        ],
      },
    ],
    generationConfig: {
      temperature: 0.2,
      maxOutputTokens: 2048,
      responseMimeType: 'application/json',
    },
    safetySettings: [
      {
        category: 'HARM_CATEGORY_HARASSMENT',
        threshold: 'BLOCK_NONE',
      },
      {
        category: 'HARM_CATEGORY_HATE_SPEECH',
        threshold: 'BLOCK_NONE',
      },
      {
        category: 'HARM_CATEGORY_SEXUALLY_EXPLICIT',
        threshold: 'BLOCK_NONE',
      },
      {
        category: 'HARM_CATEGORY_DANGEROUS_CONTENT',
        threshold: 'BLOCK_NONE',
      },
    ],
  };
}

// -----------------------------------------------------------------------------
// API Call
// -----------------------------------------------------------------------------

async function callGeminiApi(
  request: GeminiRequest,
  apiKey: string
): Promise<GeminiResponse> {
  const url = `${config.gemini.baseUrl}/models/${config.gemini.model}:generateContent?key=${apiKey}`;

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(request),
  });

  const data = await response.json() as GeminiResponse;

  if (!response.ok) {
    const errorCode = response.status === 429 ? 'RATE_LIMITED' : 'API_ERROR';
    throw new GeminiError(
      errorCode,
      data.error?.message || `HTTP ${response.status}`,
      response.status
    );
  }

  if (data.error) {
    throw new GeminiError(
      'API_ERROR',
      data.error.message,
      data.error.code
    );
  }

  return data;
}

// -----------------------------------------------------------------------------
// Response Parsing
// -----------------------------------------------------------------------------

function parseResponse(response: GeminiResponse, mode: AnalysisMode): ScanResult {
  const candidate = response.candidates?.[0];

  if (!candidate?.content?.parts?.[0]?.text) {
    throw new GeminiError('INVALID_RESPONSE', 'No content in response');
  }

  const text = candidate.content.parts[0].text;

  try {
    const parsed = JSON.parse(text) as {
      tank_health?: string;
      summary?: string;
      identifications?: Identification[];
      recommendations?: string[];
    };

    // Validate and normalize the response
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
    logger.error('Failed to parse Gemini response', {
      mode,
      response_text: text.substring(0, 500),
      error: parseError instanceof Error ? parseError.message : 'Parse error',
    });

    throw new GeminiError('PARSE_ERROR', 'Failed to parse AI response');
  }
}

function validateTankHealth(health: string | undefined): ScanResult['tank_health'] {
  const validValues = ['Excellent', 'Good', 'Fair', 'Needs Attention', 'Critical'] as const;
  if (health && validValues.includes(health as typeof validValues[number])) {
    return health as ScanResult['tank_health'];
  }
  return 'Good'; // Default
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
// Error Class
// -----------------------------------------------------------------------------

export class GeminiError extends Error {
  code: string;
  statusCode?: number;

  constructor(code: string, message: string, statusCode?: number) {
    super(message);
    this.code = code;
    this.statusCode = statusCode;
    this.name = 'GeminiError';
  }
}
