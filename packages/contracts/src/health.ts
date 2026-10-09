export type Portion = 0 | 0.25 | 0.5 | 0.75 | 1;

export interface MealRecord {
  id: string;
  studentId: string;
  dishId: string;
  portion: Portion;
  consumedGrams?: number;
  createdAt: string;
}

export interface ActivitySummary {
  date: string;
  outsideSchoolMinutes: number;
  peMinutes: number;
  targetMinutes: number;
}

export interface DailySummary {
  date: string;
  nutrition: {
    calories?: number;
    protein?: number;
    carbs?: number;
    fat?: number;
  };
  activity: ActivitySummary;
}
