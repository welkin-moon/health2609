/**
 * Strips markdown code fences (```json ... ``` or ``` ... ```) from LLM output.
 * Handles surrounding prose, leading/trailing whitespace, and newlines.
 */
export function stripCodeFences(text) {
  if (typeof text !== "string") return "";
  const trimmed = text.trim();
  
  // Case 1: string is enclosed by ```json ... ``` or ``` ... ```
  const enclosedMatch = trimmed.match(/^```(?:json)?\s*([\s\S]*?)\s*```$/i);
  if (enclosedMatch) {
    return enclosedMatch[1].trim();
  }

  // Case 2: code fence embedded somewhere inside surrounding commentary
  const embeddedMatch = trimmed.match(/```(?:json)?\s*([\s\S]*?)\s*```/i);
  if (embeddedMatch) {
    return embeddedMatch[1].trim();
  }

  return trimmed;
}

/**
 * Safely parses JSON from AI/LLM responses.
 * Attempts direct JSON parse first; if that fails, strips code fences and retries;
 * if that still fails, attempts extracting the outermost JSON object {...}.
 */
export function safeParseJson(value) {
  if (value == null) return null;
  const str = String(value).trim();
  if (!str) return null;

  try {
    return JSON.parse(str);
  } catch {
    // Attempt parsing with code fences stripped
    const stripped = stripCodeFences(str);
    try {
      return JSON.parse(stripped);
    } catch {
      // Fallback: extract substring between first '{' and last '}'
      const firstBrace = str.indexOf("{");
      const lastBrace = str.lastIndexOf("}");
      if (firstBrace !== -1 && lastBrace > firstBrace) {
        try {
          return JSON.parse(str.slice(firstBrace, lastBrace + 1));
        } catch {
          return null;
        }
      }
      return null;
    }
  }
}
