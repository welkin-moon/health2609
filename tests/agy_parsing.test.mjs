import test, { describe, it } from "node:test";
import assert from "node:assert/strict";
import { stripCodeFences, safeParseJson } from "../tools/agy-bridge/parser.mjs";

describe("AGY AI Output: Markdown Code Fence Stripping", () => {
  it("preserves plain text without code fences", () => {
    const raw = '{"schemaVersion": 1, "items": []}';
    assert.strictEqual(stripCodeFences(raw), raw);
  });

  it("strips standard ```json ... ``` code fences", () => {
    const fenced = "```json\n{\"schemaVersion\": 1, \"items\": []}\n```";
    assert.strictEqual(stripCodeFences(fenced), '{"schemaVersion": 1, "items": []}');
  });

  it("strips plain ``` ... ``` code fences without language tag", () => {
    const fenced = "```\n{\"key\": \"value\"}\n```";
    assert.strictEqual(stripCodeFences(fenced), '{"key": "value"}');
  });

  it("handles case-insensitive language tag (e.g. ```JSON)", () => {
    const fenced = "```JSON\n{\"status\": \"ok\"}\n```";
    assert.strictEqual(stripCodeFences(fenced), '{"status": "ok"}');
  });

  it("extracts embedded code fences from surrounding AI prose commentary", () => {
    const conversational = [
      "Here is the meal inspection analysis you requested:",
      "",
      "```json",
      '{"schemaVersion": 1, "items": [{"name": "番茄炒蛋"}], "notes": ["已核实"]}',
      "```",
      "",
      "Please let me know if you need more details!"
    ].join("\n");

    const expected = '{"schemaVersion": 1, "items": [{"name": "番茄炒蛋"}], "notes": ["已核实"]}';
    assert.strictEqual(stripCodeFences(conversational), expected);
  });

  it("handles Windows CRLF (\\r\\n) line endings within fences", () => {
    const crlfFenced = "```json\r\n{\r\n  \"schemaVersion\": 1\r\n}\r\n```";
    const stripped = stripCodeFences(crlfFenced);
    assert.ok(stripped.includes('"schemaVersion": 1'));
    assert.ok(!stripped.includes("```"));
  });

  it("handles non-string or empty inputs safely", () => {
    assert.strictEqual(stripCodeFences(""), "");
    assert.strictEqual(stripCodeFences("   "), "");
    assert.strictEqual(stripCodeFences(null), "");
    assert.strictEqual(stripCodeFences(undefined), "");
  });
});

describe("AGY AI Output: Robust JSON Parsing", () => {
  it("parses direct unformatted JSON successfully", () => {
    const input = '{"schemaVersion": 1, "items": [], "notes": []}';
    const parsed = safeParseJson(input);
    assert.deepStrictEqual(parsed, {
      schemaVersion: 1,
      items: [],
      notes: []
    });
  });

  it("parses JSON wrapped inside ```json markdown code fences", () => {
    const fenced = [
      "```json",
      JSON.stringify({
        schemaVersion: 1,
        items: [
          {
            name: "宫保鸡丁",
            estimatedGrams: 150,
            confidence: 0.95
          }
        ],
        notes: ["检测完毕"]
      }, null, 2),
      "```"
    ].join("\n");

    const parsed = safeParseJson(fenced);
    assert.ok(parsed);
    assert.strictEqual(parsed.schemaVersion, 1);
    assert.strictEqual(parsed.items.length, 1);
    assert.strictEqual(parsed.items[0].name, "宫保鸡丁");
  });

  it("parses JSON with surrounding conversational commentary from LLM", () => {
    const mixed = `
Based on visual examination of the lunch dish, here are the nutritional estimates:

\`\`\`json
{
  "schemaVersion": 1,
  "items": [
    {
      "name": "清炒西兰花",
      "estimatedGrams": 120,
      "servingMultiplier": 1.0,
      "confidence": 0.91,
      "nutrition": {
        "energyKcal": 45,
        "fiberG": 3.2
      },
      "needsConfirmation": []
    }
  ],
  "notes": ["Vegetable portion identified"]
}
\`\`\`

The portion is approximately one standard serving bowl.
`;
    const parsed = safeParseJson(mixed);
    assert.ok(parsed);
    assert.strictEqual(parsed.schemaVersion, 1);
    assert.strictEqual(parsed.items[0].name, "清炒西兰花");
    assert.strictEqual(parsed.items[0].nutrition.energyKcal, 45);
  });

  it("fallback-extracts JSON between outermost braces if fences are irregular", () => {
    const irregular = "Result:\n{ \"schemaVersion\": 1, \"status\": \"success\" }\nEnd of message";
    const parsed = safeParseJson(irregular);
    assert.deepStrictEqual(parsed, {
      schemaVersion: 1,
      status: "success"
    });
  });

  it("returns null for non-JSON or malformed outputs without throwing", () => {
    const invalidInputs = [
      "I cannot identify any food items in this photo.",
      "Internal Server Error: model process exited with code 1",
      "{ schemaVersion: 1, unquotedKey: invalid }", // Invalid JSON syntax
      "```json\n{ incomplete json\n```",
      "",
      "    ",
      null,
      undefined
    ];

    for (const input of invalidInputs) {
      const parsed = safeParseJson(input);
      assert.strictEqual(parsed, null, `Expected null for input: ${input}`);
    }
  });

  it("preserves unicode and Chinese characters accurately", () => {
    const unicodeInput = '```json\n{"mealName": "鱼香肉丝", "remarks": "少油少盐"}\n```';
    const parsed = safeParseJson(unicodeInput);
    assert.deepStrictEqual(parsed, {
      mealName: "鱼香肉丝",
      remarks: "少油少盐"
    });
  });
});
