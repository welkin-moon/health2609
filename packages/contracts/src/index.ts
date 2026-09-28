import { z } from "zod";

export const mealSlotSchema = z.enum(["breakfast", "lunch", "dinner"]);
export const portionSchema = z.number().min(0).max(5);

export const nutritionSchema = z.object({
  energyKcal: z.number().nonnegative().optional(),
  proteinG: z.number().nonnegative().optional(),
  fatG: z.number().nonnegative().optional(),
  carbohydrateG: z.number().nonnegative().optional()
});

export const dishSchema = z.object({
  id: z.string(),
  name: z.string().min(1).max(80),
  standardServingGrams: z.number().positive().nullable(),
  nutritionPerServing: nutritionSchema.nullable()
});

export const todayMenuSchema = z.object({
  date: z.string(),
  mealSlot: mealSlotSchema,
  dishes: z.array(dishSchema)
});

export const recordMealSchema = z.object({
  date: z.string(),
  mealSlot: mealSlotSchema,
  items: z.array(z.object({
    dishId: z.string(),
    servingMultiplier: portionSchema
  })).max(40)
});

export const adminUpsertMenuSchema = z.object({
  date: z.string(),
  mealSlot: mealSlotSchema,
  dishes: z.array(z.object({
    name: z.string().trim().min(1).max(80),
    standardServingGrams: z.number().positive().max(3000).nullable(),
    nutritionPerServing: nutritionSchema.nullable()
  })).min(1).max(40)
});

export const outsideActivitySchema = z.object({
  date: z.string(),
  exerciseMinutes: z.number().int().min(0).max(1440),
  steps: z.number().int().min(0).max(200000).optional(),
  activeEnergyKcal: z.number().min(0).max(20000).optional()
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

export type HomeMealAnalysisResult = z.infer<typeof homeMealAnalysisResultSchema>;
export type RecordMealInput = z.infer<typeof recordMealSchema>;
export type OutsideActivityInput = z.infer<typeof outsideActivitySchema>;
export type AdminUpsertMenuInput = z.infer<typeof adminUpsertMenuSchema>;
