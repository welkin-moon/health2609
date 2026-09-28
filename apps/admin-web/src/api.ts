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
};

export type DishDraft = {
  name: string;
  standardServingGrams: number | null;
  nutritionPerServing: Nutrition | null;
};

export type Overview = {
  date: string;
  meal?: {
    participants?: number;
    avg_serving_multiplier?: number;
    participationRate?: number;
  };
  pe?: {
    avg_pe_minutes?: number;
    recorded_sessions?: number;
  };
  activity?: {
    avg_outside_minutes?: number;
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
    return json<Overview>(`/v1/admin/stats/overview?date=${encodeURIComponent(date)}`);
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
  saveMenu(date: string, mealSlot: "breakfast" | "lunch" | "dinner", dishes: DishDraft[]) {
    return json<{ ok: true; menuId: string }>("/v1/admin/menus", {
      method: "PUT",
      body: JSON.stringify({ date, mealSlot, dishes })
    });
  }
};
