import test, { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  isoDateSchema,
  nutritionSchema,
  dishSchema,
  todayMenuSchema,
  recordMealSchema,
  confirmedHomeMealSchema,
  homeMealAnalysisResultSchema,
  outsideActivitySchema,
  adminPeSessionSchema,
  mealSlotSchema,
  portionSchema
} from "../packages/contracts/src/index.ts";

describe("Contracts: ISO Date Validation", () => {
  it("accepts valid ISO calendar dates (YYYY-MM-DD)", () => {
    const validDates = [
      "2026-09-29",
      "2025-01-01",
      "2024-02-29", // Leap year
      "2023-12-31",
      "2000-02-29"  // Century leap year
    ];
    for (const d of validDates) {
      const res = isoDateSchema.safeParse(d);
      assert.strictEqual(res.success, true, `Expected valid date for "${d}"`);
      assert.strictEqual(res.data, d);
    }
  });

  it("rejects invalid calendar dates and out-of-range months/days", () => {
    const invalidDates = [
      "2026-02-30", // February 30 never exists
      "2025-02-29", // 2025 is not a leap year
      "2026-13-01", // Month 13
      "2026-00-10", // Month 00
      "2026-04-31", // April has 30 days
      "2026-09-32", // Day 32
      "2026-09-00", // Day 00
      "1900-02-29"  // 1900 is not a leap year
    ];
    for (const d of invalidDates) {
      const res = isoDateSchema.safeParse(d);
      assert.strictEqual(res.success, false, `Expected invalid date for "${d}"`);
    }
  });

  it("rejects non-ISO formats, malformed strings, and non-strings", () => {
    const malformed = [
      "2026/09/29",
      "29-09-2026",
      "2026-9-29",
      "2026-09-9",
      "September 29, 2026",
      "",
      "   ",
      "not-a-date",
      "2026-09-29T12:00:00Z",
      null,
      undefined,
      12345,
      {},
      []
    ];
    for (const val of malformed) {
      const res = isoDateSchema.safeParse(val);
      assert.strictEqual(res.success, false, `Expected failure for input: ${val}`);
    }
  });

  it("validates date fields in composite domain schemas", () => {
    // Valid in todayMenuSchema
    const validMenu = todayMenuSchema.safeParse({
      date: "2026-09-29",
      mealSlot: "lunch",
      dishes: []
    });
    assert.strictEqual(validMenu.success, true);

    // Invalid date in todayMenuSchema
    const invalidMenu = todayMenuSchema.safeParse({
      date: "2026-13-01",
      mealSlot: "lunch",
      dishes: []
    });
    assert.strictEqual(invalidMenu.success, false);

    // Invalid date in recordMealSchema
    const invalidRecordMeal = recordMealSchema.safeParse({
      date: "2026-02-30",
      mealSlot: "breakfast",
      items: [{ dishId: "dish-1", servingMultiplier: 1 }]
    });
    assert.strictEqual(invalidRecordMeal.success, false);

    // Invalid date in outsideActivitySchema
    const invalidActivity = outsideActivitySchema.safeParse({
      date: "bad-date",
      exerciseMinutes: 60
    });
    assert.strictEqual(invalidActivity.success, false);

    // Invalid date in adminPeSessionSchema
    const invalidPe = adminPeSessionSchema.safeParse({
      timetableId: "tt-1",
      date: "2026/09/29",
      actualActivityMinutes: 45
    });
    assert.strictEqual(invalidPe.success, false);
  });
});

describe("Contracts: Nutrition Schema Numbers and Nullish Handling", () => {
  it("accepts empty object (all nutrition fields optional)", () => {
    const res = nutritionSchema.safeParse({});
    assert.strictEqual(res.success, true);
    assert.deepStrictEqual(res.data, {});
  });

  it("accepts valid non-negative numbers and decimals", () => {
    const validNutrition = {
      energyKcal: 450.5,
      proteinG: 22.4,
      fatG: 14.0,
      carbohydrateG: 58.2,
      fiberG: 3.5,
      sodiumMg: 420.0,
      sugarG: 6.8,
      saturatedFatG: 2.1
    };
    const res = nutritionSchema.safeParse(validNutrition);
    assert.strictEqual(res.success, true);
    assert.deepStrictEqual(res.data, validNutrition);
  });

  it("accepts zero values for all nutrients", () => {
    const zeros = {
      energyKcal: 0,
      proteinG: 0,
      fatG: 0,
      carbohydrateG: 0,
      fiberG: 0,
      sodiumMg: 0,
      sugarG: 0,
      saturatedFatG: 0
    };
    const res = nutritionSchema.safeParse(zeros);
    assert.strictEqual(res.success, true);
    assert.deepStrictEqual(res.data, zeros);
  });

  it("accepts null and undefined for nullish fields", () => {
    const nullishNutrition = {
      energyKcal: null,
      proteinG: undefined,
      fatG: 12.5,
      carbohydrateG: null,
      fiberG: 2.0
    };
    const res = nutritionSchema.safeParse(nullishNutrition);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.energyKcal, null);
    assert.strictEqual(res.data.fatG, 12.5);
    assert.strictEqual(res.data.carbohydrateG, null);
  });

  it("rejects negative numbers for any nutrient", () => {
    const testCases = [
      { energyKcal: -1 },
      { proteinG: -0.1 },
      { fatG: -5 },
      { carbohydrateG: -0.01 },
      { fiberG: -1 },
      { sodiumMg: -100 },
      { sugarG: -0.5 },
      { saturatedFatG: -2 }
    ];
    for (const tc of testCases) {
      const res = nutritionSchema.safeParse(tc);
      assert.strictEqual(res.success, false, `Expected failure for: ${JSON.stringify(tc)}`);
    }
  });

  it("rejects non-numeric invalid types", () => {
    const invalidTypes = [
      { energyKcal: "450" },
      { proteinG: true },
      { fatG: [10] },
      { carbohydrateG: { value: 50 } },
      { sodiumMg: NaN }
    ];
    for (const tc of invalidTypes) {
      const res = nutritionSchema.safeParse(tc);
      assert.strictEqual(res.success, false, `Expected failure for: ${JSON.stringify(tc)}`);
    }
  });

  it("handles nullable nutrition in dishSchema", () => {
    // Null nutritionPerServing
    const dishWithNull = dishSchema.safeParse({
      id: "dish-null",
      name: "米饭",
      standardServingGrams: 150,
      nutritionPerServing: null
    });
    assert.strictEqual(dishWithNull.success, true);
    assert.strictEqual(dishWithNull.data.nutritionPerServing, null);

    // Present nutritionPerServing
    const dishWithNutrition = dishSchema.safeParse({
      id: "dish-full",
      name: "宫保鸡丁",
      standardServingGrams: 150,
      nutritionPerServing: {
        energyKcal: 280,
        proteinG: 24,
        fatG: 14,
        carbohydrateG: 12
      }
    });
    assert.strictEqual(dishWithNutrition.success, true);
    assert.strictEqual(dishWithNutrition.data.nutritionPerServing?.energyKcal, 280);
  });
});

describe("Contracts: Home Meal Result Validation", () => {
  it("validates compliant HomeMealAnalysisResult from AGY output", () => {
    const validResult = {
      schemaVersion: 1,
      items: [
        {
          name: "清蒸鲈鱼",
          estimatedGrams: 180,
          servingMultiplier: 1.0,
          confidence: 0.95,
          nutrition: {
            energyKcal: 190,
            proteinG: 32,
            fatG: 6,
            carbohydrateG: 1,
            sodiumMg: 420
          },
          needsConfirmation: []
        },
        {
          name: "白灼菜心",
          estimatedGrams: 120,
          servingMultiplier: 1.0,
          confidence: 0.88,
          nutrition: {
            energyKcal: 45,
            proteinG: 2.5,
            fatG: 1.0,
            carbohydrateG: 6,
            fiberG: 3.0
          },
          needsConfirmation: ["份量可能偏小"]
        }
      ],
      notes: ["识别置信度高", "建议补充全谷物主食"]
    };

    const res = homeMealAnalysisResultSchema.safeParse(validResult);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.items.length, 2);
    assert.strictEqual(res.data.schemaVersion, 1);
  });

  it("accepts nullish estimates and null nutrition in HomeMealAnalysisResult", () => {
    const resultWithNulls = {
      schemaVersion: 1,
      items: [
        {
          name: "未知汤品",
          estimatedGrams: null,
          servingMultiplier: null,
          confidence: 0.45,
          nutrition: null,
          needsConfirmation: ["请确认汤品类别与重量"]
        }
      ],
      notes: []
    };

    const res = homeMealAnalysisResultSchema.safeParse(resultWithNulls);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.items[0].nutrition, null);
    assert.strictEqual(res.data.items[0].estimatedGrams, null);
  });

  it("rejects invalid HomeMealAnalysisResult schemas", () => {
    // Missing schemaVersion
    assert.strictEqual(
      homeMealAnalysisResultSchema.safeParse({ items: [], notes: [] }).success,
      false
    );

    // Unsupported schemaVersion
    assert.strictEqual(
      homeMealAnalysisResultSchema.safeParse({ schemaVersion: 2, items: [], notes: [] }).success,
      false
    );

    // Negative estimatedGrams
    assert.strictEqual(
      homeMealAnalysisResultSchema.safeParse({
        schemaVersion: 1,
        items: [
          {
            name: "菜肴",
            estimatedGrams: -10,
            servingMultiplier: 1,
            confidence: 0.9,
            nutrition: null,
            needsConfirmation: []
          }
        ],
        notes: []
      }).success,
      false
    );

    // Confidence out of [0, 1] range
    assert.strictEqual(
      homeMealAnalysisResultSchema.safeParse({
        schemaVersion: 1,
        items: [
          {
            name: "菜肴",
            estimatedGrams: 100,
            servingMultiplier: 1,
            confidence: 1.5,
            nutrition: null,
            needsConfirmation: []
          }
        ],
        notes: []
      }).success,
      false
    );

    // Serving multiplier out of [0, 10] range
    assert.strictEqual(
      homeMealAnalysisResultSchema.safeParse({
        schemaVersion: 1,
        items: [
          {
            name: "菜肴",
            estimatedGrams: 100,
            servingMultiplier: 15,
            confidence: 0.8,
            nutrition: null,
            needsConfirmation: []
          }
        ],
        notes: []
      }).success,
      false
    );

    // Empty dish name
    assert.strictEqual(
      homeMealAnalysisResultSchema.safeParse({
        schemaVersion: 1,
        items: [
          {
            name: "",
            estimatedGrams: 100,
            servingMultiplier: 1,
            confidence: 0.8,
            nutrition: null,
            needsConfirmation: []
          }
        ],
        notes: []
      }).success,
      false
    );
  });

  it("validates confirmed home meal input", () => {
    const validConfirmed = {
      date: "2026-09-29",
      mealSlot: "dinner",
      items: [
        {
          name: "红烧排骨",
          grams: 160,
          nutrition: {
            energyKcal: 380,
            proteinG: 22,
            fatG: 28,
            carbohydrateG: 8
          }
        },
        {
          name: "米饭",
          grams: null,
          nutrition: null
        }
      ]
    };

    const res = confirmedHomeMealSchema.safeParse(validConfirmed);
    assert.strictEqual(res.success, true);
    assert.strictEqual(res.data.items.length, 2);

    // Reject empty items array
    const emptyItems = confirmedHomeMealSchema.safeParse({
      date: "2026-09-29",
      mealSlot: "dinner",
      items: []
    });
    assert.strictEqual(emptyItems.success, false);

    // Reject invalid mealSlot
    const invalidSlot = confirmedHomeMealSchema.safeParse({
      date: "2026-09-29",
      mealSlot: "afternoon-snack",
      items: [{ name: "苹果", grams: 100, nutrition: null }]
    });
    assert.strictEqual(invalidSlot.success, false);

    // Reject grams > 5000
    const excessiveGrams = confirmedHomeMealSchema.safeParse({
      date: "2026-09-29",
      mealSlot: "lunch",
      items: [{ name: "西瓜", grams: 9999, nutrition: null }]
    });
    assert.strictEqual(excessiveGrams.success, false);
  });
});
