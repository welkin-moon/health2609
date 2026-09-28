const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? "http://127.0.0.1:8787";

const headers = {
  "Content-Type": "application/json",
  "x-demo-role": "admin",
  "x-demo-school": "demo-school"
};

export type Nutrition = {
  energyKcal?: number;
  proteinG?: number;
  fatG?: number;
  carbohydrateG?: number;
  fiberG?: number;
  sodiumMg?: number;
  sugarG?: number;
  saturatedFatG?: number;
};

export type DishDraft = {
  name: string;
  standardServingGrams: number | null;
  nutritionPerServing: Nutrition | null;
};

export type ClassGroup = {
  id: string;
  name: string;
};

export type SchoolDayWindow = {
  id: string;
  weekday: number;
  startTime: string;
  endTime: string;
};

export type PeTimetableItem = {
  id: string;
  weekday: number;
  startTime: string;
  endTime: string;
};

export type PeSessionItem = {
  timetableId: string;
  weekday: number;
  startTime: string;
  endTime: string;
  actualActivityMinutes: number | null;
};

export type Overview = {
  date: string;
  meal?: {
    participants?: number;
    avg_serving_multiplier?: number;
    avg_consumed_grams?: number;
    participationRate?: number;
  };
  nutrition?: {
    avg_energy_kcal?: number;
    avg_protein_g?: number;
    avg_fat_g?: number;
    avg_carbohydrate_g?: number;
  };
  pe?: {
    avg_pe_minutes?: number;
    recorded_sessions?: number;
  };
  activity?: {
    avg_outside_minutes?: number;
    avg_active_energy_kcal?: number;
    avg_total_minutes?: number;
    target_completion_rate?: number;
  };
};

async function json<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBase}${url}`, {
    ...init,
    headers: {
      ...headers,
      ...(init?.headers ?? {})
    }
  });

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  }

  return response.json() as Promise<T>;
}

export const api = {
  overview(date: string) {
    return json<Overview>(
      `/v1/admin/stats/overview?date=${encodeURIComponent(date)}`
    );
  },

  classes() {
    return json<{ classes: ClassGroup[] }>("/v1/admin/classes");
  },

  schoolDayWindows() {
    return json<{ items: SchoolDayWindow[] }>(
      "/v1/admin/school/day-windows"
    );
  },

  saveSchoolDayWindow(input: {
    weekday: number;
    startTime: string;
    endTime: string;
  }) {
    return json<{ ok: true; id: string }>(
      "/v1/admin/school/day-windows",
      {
        method: "PUT",
        body: JSON.stringify(input)
      }
    );
  },

  menus(date: string) {
    return json<{
      date: string;
      menus: Array<{
        id: string;
        mealSlot: string;
        dishes: Array<DishDraft & { id: string }>;
      }>;
    }>(`/v1/admin/menus?date=${encodeURIComponent(date)}`);
  },

  saveMenu(
    date: string,
    mealSlot: "breakfast" | "lunch" | "dinner",
    dishes: DishDraft[]
  ) {
    return json<{ ok: true; menuId: string }>("/v1/admin/menus", {
      method: "PUT",
      body: JSON.stringify({ date, mealSlot, dishes })
    });
  },

  peTimetable(classGroupId: string) {
    return json<{ classGroupId: string; items: PeTimetableItem[] }>(
      `/v1/admin/pe/timetable?classGroupId=${encodeURIComponent(classGroupId)}`
    );
  },

  savePeTimetable(input: {
    classGroupId: string;
    weekday: number;
    startTime: string;
    endTime: string;
  }) {
    return json<{ ok: true; id: string }>("/v1/admin/pe/timetable", {
      method: "PUT",
      body: JSON.stringify(input)
    });
  },

  peSessions(date: string, classGroupId: string) {
    return json<{
      date: string;
      classGroupId: string;
      items: PeSessionItem[];
    }>(
      `/v1/admin/pe/sessions?date=${encodeURIComponent(date)}&classGroupId=${encodeURIComponent(classGroupId)}`
    );
  },

  savePeSession(input: {
    timetableId: string;
    date: string;
    actualActivityMinutes: number;
  }) {
    return json<{ ok: true }>("/v1/admin/pe/session", {
      method: "PUT",
      body: JSON.stringify(input)
    });
  }
};
