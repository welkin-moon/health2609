const apiBase = (import.meta.env.VITE_API_BASE_URL as string | undefined)?.replace(/\/$/, "") ?? (import.meta.env.DEV ? "http://127.0.0.1:8787" : "https://h2609.lunarlab.uk");

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
  id?: string;
  tempId?: string;
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
  classGroupId?: string | null;
  totalStudents?: number;
  privacyMasked?: boolean;
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
    avg_fiber_g?: number;
    avg_sodium_mg?: number;
    avg_sugar_g?: number;
    avg_saturated_fat_g?: number;
  };
  pe?: {
    avg_pe_minutes?: number;
    recorded_sessions?: number;
    scheduled_sessions?: number;
    recordCoverage?: number;
  };
  activity?: {
    avg_outside_minutes?: number;
    avg_active_energy_kcal?: number;
    avg_total_minutes?: number;
    target_completion_rate?: number;
  };
  dishes?: Array<{
    id: string;
    name: string;
    standardServingGrams: number | null;
    participants: number;
    participationRate: number;
    avgServingMultiplier: number;
    avgConsumedGrams: number | null;
    avgCompletion: number;
  }>;
  trend?: Array<{
    date: string;
    mealParticipationRate: number;
    avgTotalMinutes: number;
    targetCompletionRate: number;
  }>;
};

async function json<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${apiBase}${url}`, {
      ...init,
      headers: {
        ...headers,
        ...(init?.headers ?? {})
      }
    });
  } catch (error) {
    if (error instanceof Error && error.name === "AbortError") throw error;
    throw new Error("无法连接服务器，请检查网络后重试；未保存的输入仍保留");
  }

  if (!response.ok) {
    const payload = await response.json().catch(() => null);
    const messages: Record<string, string> = {
      date_invalid: "请选择有效日期", timetable_not_found: "这节课已不存在，请刷新页面",
      pe_minutes_exceed_lesson: "实际活动时间不能超过这节课的时长",
      pe_date_weekday_mismatch: "课程星期与日期不一致，请刷新后重试",
      invalid_date: "请选择有效日期", valid_date_required: "请选择有效日期",
      date_required: "请选择日期", date_and_class_group_required: "请选择日期和班级",
      forbidden: "当前无管理权限", class_not_found: "班级不存在，请重新选择",
      class_group_not_found: "班级不存在，请重新选择",
      class_group_required: "请选择班级", duplicate_dish_id: "菜单中有重复菜品，请刷新后重试",
      dish_scope_mismatch: "菜单数据已变化，请刷新后重试", menu_upsert_failed: "菜单暂时无法保存，请稍后重试"
    };
    throw new Error(messages[payload?.error] ?? payload?.message ?? (response.status >= 500
      ? "服务暂时无法处理，请稍后重试；未保存的输入仍保留"
      : "内容尚未保存，请检查日期、份量和必填项"));
  }

  return response.json() as Promise<T>;
}

export const api = {
  overview(date: string, classGroupId?: string, signal?: AbortSignal) {
    const params = new URLSearchParams({ date });
    if (classGroupId) params.set("classGroupId", classGroupId);
    return json<Overview>(`/v1/admin/stats/overview?${params.toString()}`, { signal });
  },

  classes(signal?: AbortSignal) {
    return json<{ classes: ClassGroup[] }>("/v1/admin/classes", { signal });
  },

  schoolDayWindows(signal?: AbortSignal) {
    return json<{ items: SchoolDayWindow[] }>(
      "/v1/admin/school/day-windows",
      { signal }
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

  menus(date: string, signal?: AbortSignal) {
    return json<{
      date: string;
      menus: Array<{
        id: string;
        mealSlot: string;
        dishes: Array<DishDraft & { id: string }>;
      }>;
    }>(`/v1/admin/menus?date=${encodeURIComponent(date)}`, { signal });
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

  peSessions(date: string, classGroupId: string, signal?: AbortSignal) {
    return json<{
      date: string;
      classGroupId: string;
      items: PeSessionItem[];
    }>(
      `/v1/admin/pe/sessions?date=${encodeURIComponent(date)}&classGroupId=${encodeURIComponent(classGroupId)}`, { signal }
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
