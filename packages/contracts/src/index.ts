import { z } from "zod";

export const isoDateSchema = z
  .string()
  .regex(/^\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\d|3[01])$/, "Invalid ISO date YYYY-MM-DD");

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
export type ManualActivityInput = z.infer<typeof manualActivitySchema>;
export type AdminUpsertMenuInput = z.infer<typeof adminUpsertMenuSchema>;
export type ConfirmedHomeMealInput = z.infer<typeof confirmedHomeMealSchema>;
export type SaveSchoolMenuInput = z.infer<typeof saveSchoolMenuSchema>;
export type PeSessionInput = z.infer<typeof peSessionSchema>;
export type MenuQueryInput = z.infer<typeof menuQuerySchema>;
export type TodayQueryInput = z.infer<typeof todayQuerySchema>;
