import test, { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  calculateServingNutrition,
  calculateMacroEnergy,
  calculateGramsMultiplier,
  aggregateNutrients
} from "../packages/contracts/src/index.ts";

describe("Nutrition Math: Portions and Custom Multipliers", () => {
  const baseDishNutrition = {
    energyKcal: 280,
    proteinG: 24,
    fatG: 14,
    carbohydrateG: 12,
    fiberG: 2.5,
    sodiumMg: 450,
    sugarG: 5.2,
    saturatedFatG: 3.1
  };

  it("calculates standard 1.0 portion identically to base nutrition", () => {
    const calculated = calculateServingNutrition(baseDishNutrition, 1.0);
    assert.deepStrictEqual(calculated, {
      energyKcal: 280,
      proteinG: 24,
      fatG: 14,
      carbohydrateG: 12,
      fiberG: 2.5,
      sodiumMg: 450,
      sugarG: 5.2,
      saturatedFatG: 3.1
    });
  });

  it("calculates half portion (0.5x) deterministically", () => {
    const calculated = calculateServingNutrition(baseDishNutrition, 0.5);
    assert.strictEqual(calculated.energyKcal, 140);
    assert.strictEqual(calculated.proteinG, 12);
    assert.strictEqual(calculated.fatG, 7);
    assert.strictEqual(calculated.carbohydrateG, 6);
    assert.strictEqual(calculated.fiberG, 1.25);
    assert.strictEqual(calculated.sodiumMg, 225);
    assert.strictEqual(calculated.sugarG, 2.6);
    assert.strictEqual(calculated.saturatedFatG, 1.55);
  });

  it("calculates double portion (2.0x) deterministically", () => {
    const calculated = calculateServingNutrition(baseDishNutrition, 2.0);
    assert.strictEqual(calculated.energyKcal, 560);
    assert.strictEqual(calculated.proteinG, 48);
    assert.strictEqual(calculated.fatG, 28);
    assert.strictEqual(calculated.carbohydrateG, 24);
    assert.strictEqual(calculated.fiberG, 5.0);
    assert.strictEqual(calculated.sodiumMg, 900);
    assert.strictEqual(calculated.sugarG, 10.4);
    assert.strictEqual(calculated.saturatedFatG, 6.2);
  });

  it("handles custom fractional multipliers (e.g. 1.25x)", () => {
    const calculated = calculateServingNutrition(baseDishNutrition, 1.25);
    assert.strictEqual(calculated.energyKcal, 350);
    assert.strictEqual(calculated.proteinG, 30);
    assert.strictEqual(calculated.fatG, 17.5);
    assert.strictEqual(calculated.carbohydrateG, 15);
  });

  it("yields zeroes for 0.0 multiplier or negative multipliers", () => {
    const zeroPortion = calculateServingNutrition(baseDishNutrition, 0);
    assert.strictEqual(zeroPortion.energyKcal, 0);
    assert.strictEqual(zeroPortion.proteinG, 0);

    const negativePortion = calculateServingNutrition(baseDishNutrition, -1.5);
    assert.strictEqual(negativePortion.energyKcal, 0);
    assert.strictEqual(negativePortion.proteinG, 0);
  });

  it("handles null or undefined nutrition gracefully", () => {
    const fromNull = calculateServingNutrition(null, 1.5);
    assert.strictEqual(fromNull.energyKcal, 0);
    assert.strictEqual(fromNull.proteinG, 0);

    const fromUndefined = calculateServingNutrition(undefined, 2.0);
    assert.strictEqual(fromUndefined.energyKcal, 0);
    assert.strictEqual(fromUndefined.fatG, 0);
  });

  it("calculates multiplier from consumed grams and standard grams", () => {
    assert.strictEqual(calculateGramsMultiplier(150, 150), 1.0);
    assert.strictEqual(calculateGramsMultiplier(75, 150), 0.5);
    assert.strictEqual(calculateGramsMultiplier(300, 150), 2.0);
    assert.strictEqual(calculateGramsMultiplier(120, 160), 0.75);
    assert.strictEqual(calculateGramsMultiplier(0, 150), 0);

    // Invalid grams return null
    assert.strictEqual(calculateGramsMultiplier(null, 150), null);
    assert.strictEqual(calculateGramsMultiplier(150, null), null);
    assert.strictEqual(calculateGramsMultiplier(150, 0), null);
    assert.strictEqual(calculateGramsMultiplier(-50, 150), null);
  });
});

describe("Nutrition Math: Sum of Macros (Atwater System)", () => {
  it("calculates caloric energy using 4-9-4 kcal/g factors", () => {
    // 25g protein (100 kcal), 15g fat (135 kcal), 60g carbs (240 kcal) => 475 kcal
    const energy = calculateMacroEnergy(25, 15, 60);
    assert.strictEqual(energy, 475);
  });

  it("calculates single macro sources accurately", () => {
    // Pure protein (4 kcal/g)
    assert.strictEqual(calculateMacroEnergy(30, 0, 0), 120);

    // Pure fat (9 kcal/g)
    assert.strictEqual(calculateMacroEnergy(0, 10, 0), 90);

    // Pure carbohydrate (4 kcal/g)
    assert.strictEqual(calculateMacroEnergy(0, 0, 50), 200);
  });

  it("handles decimal values with deterministic rounding", () => {
    // 12.5g protein (50 kcal) + 8.2g fat (73.8 kcal) + 45.3g carbs (181.2 kcal) = 305 kcal
    const energy = calculateMacroEnergy(12.5, 8.2, 45.3);
    assert.strictEqual(energy, 305);
  });

  it("treats nullish or negative values as zero", () => {
    assert.strictEqual(calculateMacroEnergy(null, 10, null), 90);
    assert.strictEqual(calculateMacroEnergy(undefined, undefined, 25), 100);
    assert.strictEqual(calculateMacroEnergy(-5, -10, 20), 80);
    assert.strictEqual(calculateMacroEnergy(null, null, null), 0);
  });
});

describe("Nutrition Math: Multi-dish Aggregation", () => {
  it("aggregates multiple dishes into total daily nutrients", () => {
    const dish1 = {
      energyKcal: 280,
      proteinG: 24,
      fatG: 14,
      carbohydrateG: 12,
      fiberG: 2,
      sodiumMg: 450,
      sugarG: 5,
      saturatedFatG: 3
    };
    const dish2 = {
      energyKcal: 75,
      proteinG: 3,
      fatG: 4,
      carbohydrateG: 8,
      fiberG: 4,
      sodiumMg: 180,
      sugarG: 2,
      saturatedFatG: 0.5
    };
    const dish3 = {
      energyKcal: 230,
      proteinG: 5,
      fatG: 1,
      carbohydrateG: 48,
      fiberG: 3,
      sodiumMg: 5,
      sugarG: 0,
      saturatedFatG: 0.2
    };

    const total = aggregateNutrients([dish1, dish2, dish3]);
    assert.strictEqual(total.energyKcal, 585);
    assert.strictEqual(total.proteinG, 32);
    assert.strictEqual(total.fatG, 19);
    assert.strictEqual(total.carbohydrateG, 68);
    assert.strictEqual(total.fiberG, 9);
    assert.strictEqual(total.sodiumMg, 635);
    assert.strictEqual(total.sugarG, 7);
    assert.strictEqual(total.saturatedFatG, 3.7);
  });

  it("handles empty list or list containing nullish entries", () => {
    const emptyTotal = aggregateNutrients([]);
    assert.strictEqual(emptyTotal.energyKcal, 0);
    assert.strictEqual(emptyTotal.proteinG, 0);

    const withNulls = aggregateNutrients([
      null,
      { energyKcal: 150, proteinG: 10 },
      undefined,
      { energyKcal: 200, proteinG: 15 }
    ]);
    assert.strictEqual(withNulls.energyKcal, 350);
    assert.strictEqual(withNulls.proteinG, 25);
    assert.strictEqual(withNulls.fatG, 0);
  });

  it("avoids IEEE-754 precision errors (e.g. 0.1 + 0.2)", () => {
    const item1 = { fatG: 0.1, proteinG: 0.2 };
    const item2 = { fatG: 0.2, proteinG: 0.1 };
    const total = aggregateNutrients([item1, item2]);
    assert.strictEqual(total.fatG, 0.3);
    assert.strictEqual(total.proteinG, 0.3);
  });
});
