import test, { describe, it } from "node:test";
import assert from "node:assert/strict";
import {
  MIN_COHORT_SIZE,
  isCohortPrivacyMasked,
  maskNutritionCohort
} from "../packages/contracts/src/index.ts";

describe("Privacy Guard: Minimum Cell K-Anonymity Threshold", () => {
  it("defines minimum cohort size constant as 3 (k=3)", () => {
    assert.strictEqual(MIN_COHORT_SIZE, 3);
  });

  it("identifies cohorts below 3 as requiring privacy masking", () => {
    assert.strictEqual(isCohortPrivacyMasked(0), true, "Cohort of 0 must be masked");
    assert.strictEqual(isCohortPrivacyMasked(1), true, "Cohort of 1 must be masked (prevents single-student disclosure)");
    assert.strictEqual(isCohortPrivacyMasked(2), true, "Cohort of 2 must be masked (prevents differencing disclosure)");
    assert.strictEqual(isCohortPrivacyMasked(-1), true, "Negative count must be masked");
  });

  it("identifies cohorts >= 3 as satisfying k-anonymity (no masking needed)", () => {
    assert.strictEqual(isCohortPrivacyMasked(3), false, "Cohort of 3 satisfies k-anonymity");
    assert.strictEqual(isCohortPrivacyMasked(4), false, "Cohort of 4 satisfies k-anonymity");
    assert.strictEqual(isCohortPrivacyMasked(25), false, "Full class size satisfies k-anonymity");
    assert.strictEqual(isCohortPrivacyMasked(500), false, "School-wide cohort satisfies k-anonymity");
  });

  it("supports customizable minimum cohort threshold", () => {
    // When custom k=5 is required
    assert.strictEqual(isCohortPrivacyMasked(4, 5), true);
    assert.strictEqual(isCohortPrivacyMasked(5, 5), false);
    assert.strictEqual(isCohortPrivacyMasked(6, 5), false);
  });
});

describe("Privacy Guard: Nutrient Average Masking and Suppression", () => {
  const sampleAverages = {
    avg_energy_kcal: 650.5,
    avg_protein_g: 28.0,
    avg_fat_g: 22.5,
    avg_carbohydrate_g: 80.0,
    avg_fiber_g: 6.2,
    avg_sodium_mg: 720.0,
    avg_sugar_g: 8.5,
    avg_saturated_fat_g: 4.5
  };

  it("suppresses all nutrient averages to null when cohort = 0", () => {
    const result = maskNutritionCohort(sampleAverages, 0);
    assert.strictEqual(result.masked, true);
    assert.strictEqual(result.avg_energy_kcal, null);
    assert.strictEqual(result.avg_protein_g, null);
    assert.strictEqual(result.avg_fat_g, null);
    assert.strictEqual(result.avg_carbohydrate_g, null);
    assert.strictEqual(result.avg_fiber_g, null);
    assert.strictEqual(result.avg_sodium_mg, null);
    assert.strictEqual(result.avg_sugar_g, null);
    assert.strictEqual(result.avg_saturated_fat_g, null);
  });

  it("suppresses all nutrient averages to null when cohort = 1 (single-student protection)", () => {
    const result = maskNutritionCohort(sampleAverages, 1);
    assert.strictEqual(result.masked, true);
    assert.strictEqual(result.avg_energy_kcal, null);
    assert.strictEqual(result.avg_protein_g, null);
    assert.strictEqual(result.avg_fat_g, null);
    assert.strictEqual(result.avg_carbohydrate_g, null);
    assert.strictEqual(result.avg_fiber_g, null);
    assert.strictEqual(result.avg_sodium_mg, null);
    assert.strictEqual(result.avg_sugar_g, null);
    assert.strictEqual(result.avg_saturated_fat_g, null);
  });

  it("suppresses all nutrient averages to null when cohort = 2 (differencing attack protection)", () => {
    const result = maskNutritionCohort(sampleAverages, 2);
    assert.strictEqual(result.masked, true);
    assert.strictEqual(result.avg_energy_kcal, null);
    assert.strictEqual(result.avg_protein_g, null);
    assert.strictEqual(result.avg_fat_g, null);
    assert.strictEqual(result.avg_carbohydrate_g, null);
  });

  it("preserves exact nutrient averages when cohort reaches minimum threshold (cohort = 3)", () => {
    const result = maskNutritionCohort(sampleAverages, 3);
    assert.strictEqual(result.masked, false);
    assert.strictEqual(result.avg_energy_kcal, 650.5);
    assert.strictEqual(result.avg_protein_g, 28.0);
    assert.strictEqual(result.avg_fat_g, 22.5);
    assert.strictEqual(result.avg_carbohydrate_g, 80.0);
    assert.strictEqual(result.avg_fiber_g, 6.2);
    assert.strictEqual(result.avg_sodium_mg, 720.0);
    assert.strictEqual(result.avg_sugar_g, 8.5);
    assert.strictEqual(result.avg_saturated_fat_g, 4.5);
  });

  it("preserves exact nutrient averages for large cohorts (e.g. cohort = 30)", () => {
    const result = maskNutritionCohort(sampleAverages, 30);
    assert.strictEqual(result.masked, false);
    assert.strictEqual(result.avg_energy_kcal, 650.5);
  });

  it("handles null or undefined input safely", () => {
    const nullResult = maskNutritionCohort(null, 5);
    assert.strictEqual(nullResult.masked, true);
    assert.strictEqual(nullResult.avg_energy_kcal, null);

    const undefinedResult = maskNutritionCohort(undefined, 5);
    assert.strictEqual(undefinedResult.masked, true);
    assert.strictEqual(undefinedResult.avg_energy_kcal, null);
  });
});

describe("Privacy Guard: Admin Stats Integration Simulation", () => {
  function simulateAdminOverview(stats, totalEatenStudents) {
    const isMasked = isCohortPrivacyMasked(totalEatenStudents);
    return {
      date: stats.date,
      classGroupId: stats.classGroupId,
      totalStudents: stats.totalStudents,
      privacyMasked: isMasked,
      meal: {
        participants: totalEatenStudents,
        participationRate: stats.totalStudents > 0 ? totalEatenStudents / stats.totalStudents : 0
      },
      nutrition: maskNutritionCohort(stats.rawNutrition, totalEatenStudents)
    };
  }

  it("safely masks nutrition while exposing safe aggregate metadata for cohort < 3", () => {
    const rawStats = {
      date: "2026-09-29",
      classGroupId: "demo-class-small",
      totalStudents: 2,
      rawNutrition: {
        avg_energy_kcal: 450,
        avg_protein_g: 20,
        avg_fat_g: 15,
        avg_carbohydrate_g: 60
      }
    };

    const overview = simulateAdminOverview(rawStats, 1);
    // Non-sensitive metadata remains visible for operational administration
    assert.strictEqual(overview.date, "2026-09-29");
    assert.strictEqual(overview.classGroupId, "demo-class-small");
    assert.strictEqual(overview.meal.participants, 1);
    assert.strictEqual(overview.privacyMasked, true);

    // Sensitive dietary averages are suppressed
    assert.strictEqual(overview.nutrition.avg_energy_kcal, null);
    assert.strictEqual(overview.nutrition.avg_protein_g, null);
    assert.strictEqual(overview.nutrition.masked, true);
  });

  it("presents complete nutrition data when cohort >= 3", () => {
    const rawStats = {
      date: "2026-09-29",
      classGroupId: "demo-class-regular",
      totalStudents: 35,
      rawNutrition: {
        avg_energy_kcal: 520,
        avg_protein_g: 24,
        avg_fat_g: 16,
        avg_carbohydrate_g: 72
      }
    };

    const overview = simulateAdminOverview(rawStats, 28);
    assert.strictEqual(overview.privacyMasked, false);
    assert.strictEqual(overview.nutrition.avg_energy_kcal, 520);
    assert.strictEqual(overview.nutrition.avg_protein_g, 24);
    assert.strictEqual(overview.nutrition.masked, false);
  });
});
