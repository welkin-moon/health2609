/**
 * Data Transfer Objects & Domain Models for health2609 HarmonyOS NEXT
 * Matches @health2609/contracts specification
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
  servingMultiplier?: number;
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
  estimatedGrams: number | null;
  servingMultiplier: number | null;
  confidence: number;
  nutrition: NutritionDto | null;
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
  id?: string;
  error?: string;
}

/** Local UI Dish Consumption State */
export class DishAmount {
  servingMultiplier: number = 0;
  consumedGrams: number | null = null;

  constructor(servingMultiplier: number = 0, consumedGrams: number | null = null) {
    this.servingMultiplier = servingMultiplier;
    this.consumedGrams = consumedGrams;
  }
}

/** Local UI Draft for AI recognized dish */
export class HomeMealDraftItem {
  id: string = '';
  name: string = '';
  sourceGrams: number | null = null;
  grams: number | null = null;
  confidence: number = 0;
  nutritionAtSource: NutritionDto | null = null;
  needsConfirmation: string[] = [];

  constructor(
    name: string,
    sourceGrams: number | null = null,
    grams: number | null = null,
    confidence: number = 1.0,
    nutrition: NutritionDto | null = null,
    needsConfirmation: string[] = []
  ) {
    this.id = Math.random().toString(36).substring(2, 9);
    this.name = name;
    this.sourceGrams = sourceGrams;
    this.grams = grams;
    this.confidence = confidence;
    this.nutritionAtSource = nutrition;
    this.needsConfirmation = needsConfirmation;
  }
}

/** Totals preview */
export interface NutritionTotals {
  energyKcal: number;
  proteinG: number;
  fatG: number;
  carbohydrateG: number;
}
