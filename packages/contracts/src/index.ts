import { z } from "zod";

export const isoDateSchema = z
  .string()
  .regex(/^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])$/, "Invalid ISO date YYYY-MM-DD")
  .refine((val) => {
    const [y, m, d] = val.split("-").map(Number);
    const date = new Date(Date.UTC(y, m - 1, d));
    return (
      date.getUTCFullYear() === y &&
      date.getUTCMonth() === m - 1 &&
      date.getUTCDate() === d
    );
  }, "Invalid calendar date");

export const mealSlotSchema = z.enum(["breakfast", "lunch", "dinner"]);
export const activityIntensitySchema = z.enum(["light", "moderate", "vigorous"]);
export const portionSchema = z.number().min(0).max(5);

export const nutritionSchema = z.object({
  energyKcal: z.number().nonnegative().nullish(),
  proteinG: z.number().nonnegative().nullish(),
  fatG: z.number().nonnegative().nullish(),
  carbohydrateG: z.number().nonnegative().nullish(),
  fiberG: z.number().nonnegative().nullish(),
  sodiumMg: z.number().nonnegative().nullish(),
  sugarG: z.number().nonnegative().nullish(),
  saturatedFatG: z.number().nonnegative().nullish()
});

export const dishSchema = z.object({
  id: z.string(),
  name: z.string().min(1).max(80),
  standardServingGrams: z.number().positive().nullable(),
  nutritionPerServing: nutritionSchema.nullable()
});

export const todayMenuSchema = z.object({
  date: isoDateSchema,
  mealSlot: mealSlotSchema,
  dishes: z.array(dishSchema)
});

export const mealItemSchema = z.object({
  dishId: z.string(),
  servingMultiplier: portionSchema.optional(),
  consumedGrams: z.number().min(0).max(5000).optional()
}).refine(
  (item) => item.servingMultiplier !== undefined || item.consumedGrams !== undefined,
  "servingMultiplier or consumedGrams is required"
);

export const recordMealSchema = z.object({
  date: isoDateSchema,
  mealSlot: mealSlotSchema,
  items: z.array(mealItemSchema)
    .max(40)
    .refine(
      (items) => new Set(items.map((item) => item.dishId)).size === items.length,
      "duplicate dishId"
    )
});

export const adminUpsertMenuSchema = z.object({
  date: isoDateSchema,
  mealSlot: mealSlotSchema,
  dishes: z.array(z.object({
    id: z.string().optional(),
    name: z.string().trim().min(1).max(80),
    standardServingGrams: z.number().positive().max(3000).nullable(),
    nutritionPerServing: nutritionSchema.nullable()
  })).min(1).max(40)
});

export const outsideActivitySchema = z.object({
  date: isoDateSchema,
  exerciseMinutes: z.number().int().min(0).max(1440),
  steps: z.number().int().min(0).max(200000).optional(),
  activeEnergyKcal: z.number().min(0).max(20000).optional()
});

export const schoolActivitySourceSchema = z.enum(["school", "health_connect"]);

export const schoolActivityOverrideSchema = z.object({
  date: isoDateSchema,
  source: schoolActivitySourceSchema,
  exerciseMinutes: z.number().int().min(0).max(1440).optional(),
  steps: z.number().int().min(0).max(200000).optional(),
  activeEnergyKcal: z.number().min(0).max(20000).optional()
}).superRefine((value, ctx) => {
  if (value.source === "health_connect" && value.exerciseMinutes === undefined) {
    ctx.addIssue({
      code: z.ZodIssueCode.custom,
      path: ["exerciseMinutes"],
      message: "exerciseMinutes is required for health_connect source"
    });
  }
});

export const manualActivitySchema = z.object({
  date: isoDateSchema,
  activityType: z.string().trim().min(1).max(80),
  startTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/).optional(),
  durationMinutes: z.number().int().min(1).max(600),
  intensity: activityIntensitySchema,
  estimatedActiveEnergyKcal: z.number().min(0).max(10000).optional()
});

export const energyReferenceSchema = z.object({
  dailyEnergyReferenceKcal: z.number().int().min(500).max(6000).nullable()
});

export const adminSchoolDayWindowSchema = z.object({
  weekday: z.number().int().min(1).max(7),
  startTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/),
  endTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/)
}).refine(
  (window) => window.startTime < window.endTime,
  "school day endTime must be after startTime"
);

export const adminPeTimetableSchema = z.object({
  classGroupId: z.string().min(1),
  weekday: z.number().int().min(1).max(7),
  startTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/),
  endTime: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/)
}).refine(
  (lesson) => lesson.startTime < lesson.endTime,
  "PE endTime must be after startTime"
);

export const adminPeSessionSchema = z.object({
  timetableId: z.string().min(1),
  date: isoDateSchema,
  actualActivityMinutes: z.number().int().min(0).max(300)
});

export const confirmedHomeMealSchema = z.object({
  date: isoDateSchema,
  mealSlot: mealSlotSchema,
  items: z.array(z.object({
    name: z.string().trim().min(1).max(120),
    grams: z.number().min(0).max(5000).nullable(),
    nutrition: nutritionSchema.nullable()
  })).min(1).max(30)
});

export const homeMealAnalysisResultSchema = z.object({
  schemaVersion: z.literal(1),
  items: z.array(z.object({
    name: z.string().min(1).max(120),
    estimatedGrams: z.number().positive().max(3000).nullable(),
    servingMultiplier: z.number().min(0).max(10).nullable(),
    confidence: z.number().min(0).max(1),
    nutrition: nutritionSchema.nullable(),
    needsConfirmation: z.array(z.string()).max(20)
  })).max(30),
  notes: z.array(z.string().max(240)).max(20)
});

export const menuQuerySchema = z.object({
  date: isoDateSchema,
  mealSlot: mealSlotSchema.optional()
});

export const todayQuerySchema = z.object({
  date: isoDateSchema.optional(),
  mealSlot: mealSlotSchema.optional()
});

export const saveSchoolMenuSchema = adminUpsertMenuSchema;
export const peSessionSchema = adminPeSessionSchema;

export type HomeMealAnalysisResult = z.infer<typeof homeMealAnalysisResultSchema>;
export type RecordMealInput = z.infer<typeof recordMealSchema>;
export type OutsideActivityInput = z.infer<typeof outsideActivitySchema>;
export type SchoolActivityOverrideInput = z.infer<typeof schoolActivityOverrideSchema>;
export type ManualActivityInput = z.infer<typeof manualActivitySchema>;
export type AdminUpsertMenuInput = z.infer<typeof adminUpsertMenuSchema>;
export type ConfirmedHomeMealInput = z.infer<typeof confirmedHomeMealSchema>;
export type SaveSchoolMenuInput = z.infer<typeof saveSchoolMenuSchema>;
export type PeSessionInput = z.infer<typeof peSessionSchema>;
export type MenuQueryInput = z.infer<typeof menuQuerySchema>;
export type TodayQueryInput = z.infer<typeof todayQuerySchema>;

/* -------------------------------------------------------------------------- */
/*                        Deterministic Nutrition Math                         */
/* -------------------------------------------------------------------------- */

export type NutritionValues = {
  energyKcal?: number | null;
  proteinG?: number | null;
  fatG?: number | null;
  carbohydrateG?: number | null;
  fiberG?: number | null;
  sodiumMg?: number | null;
  sugarG?: number | null;
  saturatedFatG?: number | null;
};

/**
 * Calculates nutrition for a given portion/multiplier deterministically.
 * Multiplier is bounded >= 0. Results rounded to 2 decimal places.
 */
export function calculateServingNutrition(
  nutrition: NutritionValues | null | undefined,
  multiplier: number
): Record<string, number> {
  const m = Math.max(0, Number(multiplier ?? 0));
  if (!nutrition) {
    return {
      energyKcal: 0,
      proteinG: 0,
      fatG: 0,
      carbohydrateG: 0,
      fiberG: 0,
      sodiumMg: 0,
      sugarG: 0,
      saturatedFatG: 0
    };
  }
  return {
    energyKcal: Math.round(Number(nutrition.energyKcal ?? 0) * m * 100) / 100,
    proteinG: Math.round(Number(nutrition.proteinG ?? 0) * m * 100) / 100,
    fatG: Math.round(Number(nutrition.fatG ?? 0) * m * 100) / 100,
    carbohydrateG: Math.round(Number(nutrition.carbohydrateG ?? 0) * m * 100) / 100,
    fiberG: Math.round(Number(nutrition.fiberG ?? 0) * m * 100) / 100,
    sodiumMg: Math.round(Number(nutrition.sodiumMg ?? 0) * m * 100) / 100,
    sugarG: Math.round(Number(nutrition.sugarG ?? 0) * m * 100) / 100,
    saturatedFatG: Math.round(Number(nutrition.saturatedFatG ?? 0) * m * 100) / 100
  };
}

/**
 * Calculates energy from macronutrients: 4 kcal/g protein, 9 kcal/g fat, 4 kcal/g carbohydrate.
 */
export function calculateMacroEnergy(
  proteinG: number | null | undefined,
  fatG: number | null | undefined,
  carbohydrateG: number | null | undefined
): number {
  const p = Math.max(0, Number(proteinG ?? 0));
  const f = Math.max(0, Number(fatG ?? 0));
  const c = Math.max(0, Number(carbohydrateG ?? 0));
  return Math.round((p * 4 + f * 9 + c * 4) * 100) / 100;
}

/**
 * Calculates multiplier from consumed grams and standard serving grams.
 */
export function calculateGramsMultiplier(
  consumedGrams: number | null | undefined,
  standardServingGrams: number | null | undefined
): number | null {
  if (
    consumedGrams == null ||
    standardServingGrams == null ||
    standardServingGrams <= 0 ||
    consumedGrams < 0
  ) {
    return null;
  }
  return Math.round((consumedGrams / standardServingGrams) * 1000) / 1000;
}

/**
 * Aggregates a list of nutrition items into total nutrients.
 */
export function aggregateNutrients(
  items: Array<NutritionValues | null | undefined>
): Record<string, number> {
  const total = {
    energyKcal: 0,
    proteinG: 0,
    fatG: 0,
    carbohydrateG: 0,
    fiberG: 0,
    sodiumMg: 0,
    sugarG: 0,
    saturatedFatG: 0
  };
  for (const item of items) {
    if (!item) continue;
    total.energyKcal += Number(item.energyKcal ?? 0);
    total.proteinG += Number(item.proteinG ?? 0);
    total.fatG += Number(item.fatG ?? 0);
    total.carbohydrateG += Number(item.carbohydrateG ?? 0);
    total.fiberG += Number(item.fiberG ?? 0);
    total.sodiumMg += Number(item.sodiumMg ?? 0);
    total.sugarG += Number(item.sugarG ?? 0);
    total.saturatedFatG += Number(item.saturatedFatG ?? 0);
  }
  for (const key of Object.keys(total) as Array<keyof typeof total>) {
    total[key] = Math.round(total[key] * 100) / 100;
  }
  return total;
}

/* -------------------------------------------------------------------------- */
/*                         Privacy Guard & K-Anonymity                        */
/* -------------------------------------------------------------------------- */

export const MIN_COHORT_SIZE = 3;

export type NutrientAverages = {
  avg_energy_kcal?: number | null;
  avg_protein_g?: number | null;
  avg_fat_g?: number | null;
  avg_carbohydrate_g?: number | null;
  avg_fiber_g?: number | null;
  avg_sodium_mg?: number | null;
  avg_sugar_g?: number | null;
  avg_saturated_fat_g?: number | null;
};

export type MaskedNutrientAverages = {
  avg_energy_kcal: number | null;
  avg_protein_g: number | null;
  avg_fat_g: number | null;
  avg_carbohydrate_g: number | null;
  avg_fiber_g: number | null;
  avg_sodium_mg: number | null;
  avg_sugar_g: number | null;
  avg_saturated_fat_g: number | null;
  masked?: boolean;
};

/**
 * Checks whether cohort size is below the minimum threshold for k-anonymity (default: 3).
 */
export function isCohortPrivacyMasked(
  cohortSize: number,
  minCohort: number = MIN_COHORT_SIZE
): boolean {
  return cohortSize < minCohort;
}

/**
 * Applies minimum-cell k-anonymity masking.
 * When cohort < minCohort (default 3), nutrient averages are suppressed to null to prevent
 * reverse-engineering individual students' dietary intake.
 */
export function maskNutritionCohort(
  nutrition: NutrientAverages | null | undefined,
  cohortSize: number,
  minCohort: number = MIN_COHORT_SIZE
): MaskedNutrientAverages {
  const masked = isCohortPrivacyMasked(cohortSize, minCohort);
  if (masked || !nutrition) {
    return {
      avg_energy_kcal: null,
      avg_protein_g: null,
      avg_fat_g: null,
      avg_carbohydrate_g: null,
      avg_fiber_g: null,
      avg_sodium_mg: null,
      avg_sugar_g: null,
      avg_saturated_fat_g: null,
      masked: true
    };
  }
  return {
    avg_energy_kcal: nutrition.avg_energy_kcal != null ? Number(nutrition.avg_energy_kcal) : null,
    avg_protein_g: nutrition.avg_protein_g != null ? Number(nutrition.avg_protein_g) : null,
    avg_fat_g: nutrition.avg_fat_g != null ? Number(nutrition.avg_fat_g) : null,
    avg_carbohydrate_g: nutrition.avg_carbohydrate_g != null ? Number(nutrition.avg_carbohydrate_g) : null,
    avg_fiber_g: nutrition.avg_fiber_g != null ? Number(nutrition.avg_fiber_g) : null,
    avg_sodium_mg: nutrition.avg_sodium_mg != null ? Number(nutrition.avg_sodium_mg) : null,
    avg_sugar_g: nutrition.avg_sugar_g != null ? Number(nutrition.avg_sugar_g) : null,
    avg_saturated_fat_g: nutrition.avg_saturated_fat_g != null ? Number(nutrition.avg_saturated_fat_g) : null,
    masked: false
  };
}
