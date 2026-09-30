/**
 * Data Transfer Objects (DTOs), UI State Models & Domain Computations
 * Fully aligned with @health2609/contracts specification
 * Compatible with HarmonyOS NEXT & OpenHarmony / KaihongOS
 */

export interface NutritionDto {
  energyKcal?: number | null;
  proteinG?: number | null;
  fatG?: number | null;
  carbohydrateG?: number | null;
  fiberG?: number | null;
  sodiumMg?: number | null;
  sugarG?: number | null;
  saturatedFatG?: number | null;
}

export interface DishDto {
  id: string;
  name: string;
  standardServingGrams: number | null;
  nutritionPerServing: NutritionDto | null;
}

export interface TodayMenuDto {
  date: string;
  mealSlot: string;
  dishes: DishDto[];
}

export interface MealItemRequest {
  dishId: string;
  servingMultiplier?: number | null;
  consumedGrams?: number | null;
}

export interface MealConsumptionRequest {
  date: string;
  mealSlot: string;
  items: MealItemRequest[];
}

export interface SchoolDayWindowDto {
  id: string;
  startTime: string;
  endTime: string;
}

export interface SchoolDayWindowsDto {
  date: string;
  weekday: number;
  windows: SchoolDayWindowDto[];
}

export interface OutsideSchoolActivityRequest {
  date: string;
  exerciseMinutes: number;
  steps?: number | null;
  activeEnergyKcal?: number | null;
}

export interface ManualActivityRequest {
  date: string;
  activityType: string;
  startTime?: string | null;
  durationMinutes: number;
  intensity: 'light' | 'moderate' | 'vigorous' | string;
  estimatedActiveEnergyKcal?: number | null;
}

export interface EnergyReferenceRequest {
  dailyEnergyReferenceKcal: number | null;
}

export interface MacroCompositionDto {
  protein: number;
  fat: number;
  carbohydrate: number;
}

export interface NutritionSummaryDto {
  energyKcal: number;
  proteinG: number;
  fatG: number;
  carbohydrateG: number;
  fiberG: number;
  sodiumMg: number;
  sugarG: number;
  saturatedFatG: number;
  macroCompositionPercent: MacroCompositionDto;
}

export interface IntensityMinutesDto {
  light: number;
  moderate: number;
  vigorous: number;
}

export interface ActivitySummaryDto {
  peMinutes: number;
  healthConnectOutsideMinutes: number;
  manualOutsideMinutes: number;
  outsideMinutes: number;
  totalMinutes: number;
  targetMinutes: number;
  targetReached: boolean;
  intensityMinutes: IntensityMinutesDto;
  activeEnergyKcal: number | null;
  manuallyEstimatedActiveEnergyKcal: number;
}

export interface EnergySummaryDto {
  dailyEnergyReferenceKcal: number | null;
  intakeKcal: number;
  referenceGapKcal: number | null;
}

export interface DailySummaryDto {
  date: string;
  nutrition: NutritionSummaryDto;
  activity: ActivitySummaryDto;
  energy: EnergySummaryDto;
}

export interface HomeMealAnalysisItemDto {
  name: string;
  estimatedGrams?: number | null;
  servingMultiplier?: number | null;
  confidence: number;
  nutrition?: NutritionDto | null;
  needsConfirmation: string[];
}

export interface HomeMealAnalysisResultDto {
  schemaVersion: number;
  items: HomeMealAnalysisItemDto[];
  notes: string[];
}

export interface ConfirmedHomeMealItemRequest {
  name: string;
  grams: number | null;
  nutrition: NutritionDto | null;
}

export interface ConfirmedHomeMealRequest {
  date: string;
  mealSlot: string;
  items: ConfirmedHomeMealItemRequest[];
}

export interface ApiWriteResult {
  ok: boolean;
  id?: string | null;
  error?: string;
}

/* -------------------------------------------------------------------------- */
/*                            Client UI State Models                          */
/* -------------------------------------------------------------------------- */

export class DishAmount {
  servingMultiplier: number = 0.0;
  consumedGrams: number | null = null;

  constructor(servingMultiplier: number = 0.0, consumedGrams: number | null = null) {
    this.servingMultiplier = servingMultiplier;
    this.consumedGrams = consumedGrams;
  }
}

export class HomeMealDraftItem {
  id: string = '';
  name: string = '';
  sourceGrams: number | null = null;
  grams: number | null = null;
  confidence: number = 0.9;
  nutritionAtSource: NutritionDto | null = null;
  needsConfirmation: string[] = [];

  constructor(
    name: string,
    sourceGrams: number | null = null,
    grams: number | null = null,
    confidence: number = 0.9,
    nutritionAtSource: NutritionDto | null = null,
    needsConfirmation: string[] = []
  ) {
    this.id = Math.random().toString(36).substring(2, 9);
    this.name = name;
    this.sourceGrams = sourceGrams;
    this.grams = grams;
    this.confidence = confidence;
    this.nutritionAtSource = nutritionAtSource;
    this.needsConfirmation = needsConfirmation;
  }
}

export class NutritionTotals {
  energyKcal: number = 0.0;
  proteinG: number = 0.0;
  fatG: number = 0.0;
  carbohydrateG: number = 0.0;

  constructor(
    energyKcal: number = 0.0,
    proteinG: number = 0.0,
    fatG: number = 0.0,
    carbohydrateG: number = 0.0
  ) {
    this.energyKcal = energyKcal;
    this.proteinG = proteinG;
    this.fatG = fatG;
    this.carbohydrateG = carbohydrateG;
  }
}

export class PendingOfflineRecord {
  id: string;
  timestamp: number;
  endpointType: 'meal' | 'activity_manual' | 'activity_outside' | 'home_meal' | 'energy_ref';
  payloadJson: string;

  constructor(
    id: string,
    timestamp: number,
    endpointType: 'meal' | 'activity_manual' | 'activity_outside' | 'home_meal' | 'energy_ref',
    payloadJson: string
  ) {
    this.id = id;
    this.timestamp = timestamp;
    this.endpointType = endpointType;
    this.payloadJson = payloadJson;
  }
}

/* -------------------------------------------------------------------------- */
/*                        Deterministic Nutrition Math                         */
/* -------------------------------------------------------------------------- */

export function roundOneDecimal(value: number): number {
  return Math.round(value * 10) / 10;
}

export function calculateServingNutrition(
  nutrition: NutritionDto | null | undefined,
  multiplier: number
): NutritionDto {
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

export function aggregateNutrients(
  items: Array<NutritionDto | null | undefined>
): NutritionDto {
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
  return {
    energyKcal: Math.round(total.energyKcal * 100) / 100,
    proteinG: Math.round(total.proteinG * 100) / 100,
    fatG: Math.round(total.fatG * 100) / 100,
    carbohydrateG: Math.round(total.carbohydrateG * 100) / 100,
    fiberG: Math.round(total.fiberG * 100) / 100,
    sodiumMg: Math.round(total.sodiumMg * 100) / 100,
    sugarG: Math.round(total.sugarG * 100) / 100,
    saturatedFatG: Math.round(total.saturatedFatG * 100) / 100
  };
}

/* -------------------------------------------------------------------------- */
/*                         Privacy Guard & K-Anonymity                        */
/* -------------------------------------------------------------------------- */

export const MIN_COHORT_SIZE = 3;

export interface NutrientAverages {
  avg_energy_kcal?: number | null;
  avg_protein_g?: number | null;
  avg_fat_g?: number | null;
  avg_carbohydrate_g?: number | null;
  avg_fiber_g?: number | null;
  avg_sodium_mg?: number | null;
  avg_sugar_g?: number | null;
  avg_saturated_fat_g?: number | null;
}

export interface MaskedNutrientAverages {
  avg_energy_kcal: number | null;
  avg_protein_g: number | null;
  avg_fat_g: number | null;
  avg_carbohydrate_g: number | null;
  avg_fiber_g: number | null;
  avg_sodium_mg: number | null;
  avg_sugar_g: number | null;
  avg_saturated_fat_g: number | null;
  masked?: boolean;
}

export function isCohortPrivacyMasked(
  cohortSize: number,
  minCohort: number = MIN_COHORT_SIZE
): boolean {
  return cohortSize < minCohort;
}

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