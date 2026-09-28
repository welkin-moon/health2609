import { Hono } from "hono";
import { cors } from "hono/cors";
import { zValidator } from "@hono/zod-validator";
import {
  adminPeSessionSchema,
  adminPeTimetableSchema,
  adminSchoolDayWindowSchema,
  adminUpsertMenuSchema,
  confirmedHomeMealSchema,
  energyReferenceSchema,
  homeMealAnalysisResultSchema,
  manualActivitySchema,
  outsideActivitySchema,
  recordMealSchema,
  todayMenuSchema
} from "@health2609/contracts";
import { HOME_MEAL_AGY_PROMPT } from "./integrations/agy/homeMealPrompt";

type Bindings = {
  DB: D1Database;
  AGY_TASK_URL: string;
  AGY_TASK_TOKEN?: string;
};

type Variables = {
  schoolId: string;
  participantId: string;
  role: "student" | "admin";
};

type AppEnv = {
  Bindings: Bindings;
  Variables: Variables;
};

const app = new Hono<AppEnv>();

app.use("/v1/*", cors({
  origin: "*",
  allowHeaders: [
    "Content-Type",
    "Authorization",
    "x-demo-school",
    "x-demo-participant",
    "x-demo-role"
  ],
  allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
}));

app.use("/v1/*", async (c, next) => {
  // Intentionally small competition-demo auth shell.
  const schoolId = c.req.header("x-demo-school") ?? "demo-school";
  const participantId = c.req.header("x-demo-participant") ?? "demo-student";
  const role = c.req.header("x-demo-role") === "admin" ? "admin" : "student";
  c.set("schoolId", schoolId);
  c.set("participantId", participantId);
  c.set("role", role);
  await next();
});

const requireAdmin = (role: string) => role === "admin";

function weekdayFromDate(date: string): number | null {
  const parsed = new Date(`${date}T00:00:00Z`);
  if (Number.isNaN(parsed.getTime())) return null;
  const day = parsed.getUTCDay();
  return day === 0 ? 7 : day;
}

async function membershipFor(
  db: D1Database,
  schoolId: string,
  participantId: string
) {
  return db.prepare(
    `SELECT id, class_group_id
       FROM student_memberships
      WHERE school_id = ? AND participant_id = ?
      LIMIT 1`
  ).bind(schoolId, participantId).first<{
    id: string;
    class_group_id: string | null;
  }>();
}

app.get("/health", (c) => c.json({ ok: true }));

app.get("/v1/school/day-windows", async (c) => {
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const weekday = weekdayFromDate(date);
  if (weekday == null) return c.json({ error: "invalid_date" }, 400);

  const rows = await c.env.DB.prepare(
    `SELECT id, start_time, end_time
       FROM school_day_windows
      WHERE school_id = ? AND weekday = ?
      ORDER BY start_time`
  ).bind(c.get("schoolId"), weekday).all();

  return c.json({
    date,
    weekday,
    windows: rows.results.map((row) => ({
      id: String(row.id),
      startTime: String(row.start_time),
      endTime: String(row.end_time)
    }))
  });
});

app.get("/v1/today/menu", async (c) => {
  const date = c.req.query("date");
  const mealSlot = c.req.query("mealSlot") ?? "lunch";
  if (!date) return c.json({ error: "date_required" }, 400);

  const rows = await c.env.DB.prepare(
    `SELECT d.id, d.name, d.standard_serving_grams, d.nutrition_per_serving_json
       FROM menus m
       JOIN dishes d ON d.menu_id = m.id
      WHERE m.school_id = ? AND m.date = ? AND m.meal_slot = ?
      ORDER BY d.sort_order, d.name`
  ).bind(c.get("schoolId"), date, mealSlot).all();

  const payload = todayMenuSchema.parse({
    date,
    mealSlot,
    dishes: rows.results.map((row) => ({
      id: String(row.id),
      name: String(row.name),
      standardServingGrams:
        row.standard_serving_grams == null
          ? null
          : Number(row.standard_serving_grams),
      nutritionPerServing: row.nutrition_per_serving_json
        ? JSON.parse(String(row.nutrition_per_serving_json))
        : null
    }))
  });

  return c.json(payload);
});

app.post(
  "/v1/meals/consumption",
  zValidator("json", recordMealSchema),
  async (c) => {
    const body = c.req.valid("json");
    const schoolId = c.get("schoolId");
    const membership = await membershipFor(
      c.env.DB,
      schoolId,
      c.get("participantId")
    );

    if (!membership) return c.json({ error: "membership_not_found" }, 404);

    if (!body.items.length) {
      return c.json({ ok: true });
    }

    const placeholders = body.items.map(() => "?").join(",");
    const allowed = await c.env.DB.prepare(
      `SELECT d.id, d.standard_serving_grams
         FROM dishes d
         JOIN menus m ON m.id = d.menu_id
        WHERE m.school_id = ?
          AND m.date = ?
          AND m.meal_slot = ?
          AND d.id IN (${placeholders})`
    ).bind(
      schoolId,
      body.date,
      body.mealSlot,
      ...body.items.map((item) => item.dishId)
    ).all<{
      id: string;
      standard_serving_grams: number | null;
    }>();

    if (allowed.results.length !== body.items.length) {
      return c.json({ error: "dish_scope_mismatch" }, 400);
    }

    const dishById = new Map(
      allowed.results.map((row) => [row.id, row])
    );

    const normalized: Array<{
      dishId: string;
      multiplier: number;
      grams: number | null;
    }> = [];

    for (const item of body.items) {
      const dish = dishById.get(item.dishId)!;
      const standardGrams =
        dish.standard_serving_grams == null
          ? null
          : Number(dish.standard_serving_grams);

      let multiplier = item.servingMultiplier;
      let grams = item.consumedGrams;

      if (grams !== undefined && standardGrams && standardGrams > 0) {
        multiplier = grams / standardGrams;
      } else if (
        grams === undefined &&
        multiplier !== undefined &&
        standardGrams &&
        standardGrams > 0
      ) {
        grams = standardGrams * multiplier;
      }

      if (multiplier === undefined) {
        return c.json(
          { error: "serving_multiplier_required_without_standard_grams" },
          400
        );
      }

      if (multiplier < 0 || multiplier > 5) {
        return c.json({ error: "meal_amount_out_of_range" }, 400);
      }

      normalized.push({
        dishId: item.dishId,
        multiplier,
        grams: grams ?? null
      });
    }

    const statements = normalized.map((item) =>
      c.env.DB.prepare(
        `INSERT INTO meal_consumption
           (id, student_membership_id, dish_id, serving_multiplier, consumed_grams, consumed_at)
         VALUES (?, ?, ?, ?, ?, datetime('now'))
         ON CONFLICT(student_membership_id, dish_id)
         DO UPDATE SET serving_multiplier = excluded.serving_multiplier,
                       consumed_grams = excluded.consumed_grams,
                       consumed_at = excluded.consumed_at`
      ).bind(
        crypto.randomUUID(),
        membership.id,
        item.dishId,
        item.multiplier,
        item.grams
      )
    );

    await c.env.DB.batch(statements);
    return c.json({ ok: true });
  }
);

app.post(
  "/v1/activity/outside-school",
  zValidator("json", outsideActivitySchema),
  async (c) => {
    const body = c.req.valid("json");
    const membership = await membershipFor(
      c.env.DB,
      c.get("schoolId"),
      c.get("participantId")
    );

    if (!membership) return c.json({ error: "membership_not_found" }, 404);

    await c.env.DB.prepare(
      `INSERT INTO outside_school_activity_daily
         (id, student_membership_id, date, exercise_minutes, steps, active_energy_kcal)
       VALUES (?, ?, ?, ?, ?, ?)
       ON CONFLICT(student_membership_id, date)
       DO UPDATE SET exercise_minutes = excluded.exercise_minutes,
                     steps = excluded.steps,
                     active_energy_kcal = excluded.active_energy_kcal`
    ).bind(
      crypto.randomUUID(),
      membership.id,
      body.date,
      body.exerciseMinutes,
      body.steps ?? null,
      body.activeEnergyKcal ?? null
    ).run();

    return c.json({ ok: true });
  }
);

app.get("/v1/activity/manual", async (c) => {
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const membership = await membershipFor(
    c.env.DB,
    c.get("schoolId"),
    c.get("participantId")
  );
  if (!membership) return c.json({ error: "membership_not_found" }, 404);

  const rows = await c.env.DB.prepare(
    `SELECT id, activity_type, start_time, duration_minutes, intensity,
            estimated_active_energy_kcal
       FROM manual_activity_sessions
      WHERE student_membership_id = ? AND date = ?
      ORDER BY COALESCE(start_time, '99:99'), created_at`
  ).bind(membership.id, date).all();

  return c.json({
    date,
    sessions: rows.results.map((row) => ({
      id: String(row.id),
      activityType: String(row.activity_type),
      startTime: row.start_time == null ? null : String(row.start_time),
      durationMinutes: Number(row.duration_minutes),
      intensity: String(row.intensity),
      estimatedActiveEnergyKcal:
        row.estimated_active_energy_kcal == null
          ? null
          : Number(row.estimated_active_energy_kcal)
    }))
  });
});

app.post(
  "/v1/activity/manual",
  zValidator("json", manualActivitySchema),
  async (c) => {
    const body = c.req.valid("json");
    const membership = await membershipFor(
      c.env.DB,
      c.get("schoolId"),
      c.get("participantId")
    );
    if (!membership) return c.json({ error: "membership_not_found" }, 404);

    const id = crypto.randomUUID();
    await c.env.DB.prepare(
      `INSERT INTO manual_activity_sessions
         (id, student_membership_id, date, activity_type, start_time,
          duration_minutes, intensity, estimated_active_energy_kcal)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
    ).bind(
      id,
      membership.id,
      body.date,
      body.activityType,
      body.startTime ?? null,
      body.durationMinutes,
      body.intensity,
      body.estimatedActiveEnergyKcal ?? null
    ).run();

    return c.json({ ok: true, id });
  }
);

app.get("/v1/preferences/energy-reference", async (c) => {
  const membership = await membershipFor(
    c.env.DB,
    c.get("schoolId"),
    c.get("participantId")
  );
  if (!membership) return c.json({ error: "membership_not_found" }, 404);

  const row = await c.env.DB.prepare(
    `SELECT daily_energy_reference_kcal
       FROM student_preferences
      WHERE student_membership_id = ?`
  ).bind(membership.id).first<{
    daily_energy_reference_kcal: number | null;
  }>();

  return c.json({
    dailyEnergyReferenceKcal:
      row?.daily_energy_reference_kcal == null
        ? null
        : Number(row.daily_energy_reference_kcal)
  });
});

app.put(
  "/v1/preferences/energy-reference",
  zValidator("json", energyReferenceSchema),
  async (c) => {
    const body = c.req.valid("json");
    const membership = await membershipFor(
      c.env.DB,
      c.get("schoolId"),
      c.get("participantId")
    );
    if (!membership) return c.json({ error: "membership_not_found" }, 404);

    await c.env.DB.prepare(
      `INSERT INTO student_preferences
         (student_membership_id, daily_energy_reference_kcal, updated_at)
       VALUES (?, ?, datetime('now'))
       ON CONFLICT(student_membership_id)
       DO UPDATE SET daily_energy_reference_kcal = excluded.daily_energy_reference_kcal,
                     updated_at = excluded.updated_at`
    ).bind(
      membership.id,
      body.dailyEnergyReferenceKcal
    ).run();

    return c.json({ ok: true });
  }
);

app.get("/v1/today/summary", async (c) => {
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const membership = await membershipFor(
    c.env.DB,
    c.get("schoolId"),
    c.get("participantId")
  );
  if (!membership) return c.json({ error: "membership_not_found" }, 404);

  const schoolId = c.get("schoolId");

  const [nutrition, homeMeals, pe, health, manual, preference, school] = await Promise.all([
    c.env.DB.prepare(
      `SELECT
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.energyKcal'), 0) * mc.serving_multiplier), 0) AS energy_kcal,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.proteinG'), 0) * mc.serving_multiplier), 0) AS protein_g,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.fatG'), 0) * mc.serving_multiplier), 0) AS fat_g,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.carbohydrateG'), 0) * mc.serving_multiplier), 0) AS carbohydrate_g,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.fiberG'), 0) * mc.serving_multiplier), 0) AS fiber_g,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.sodiumMg'), 0) * mc.serving_multiplier), 0) AS sodium_mg,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.sugarG'), 0) * mc.serving_multiplier), 0) AS sugar_g,
          COALESCE(SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.saturatedFatG'), 0) * mc.serving_multiplier), 0) AS saturated_fat_g
         FROM meal_consumption mc
         JOIN dishes d ON d.id = mc.dish_id
         JOIN menus m ON m.id = d.menu_id
        WHERE mc.student_membership_id = ? AND m.date = ?`
    ).bind(membership.id, date).first(),
    c.env.DB.prepare(
      `SELECT confirmed_items_json
         FROM home_meals
        WHERE student_membership_id = ? AND date = ?`
    ).bind(membership.id, date).all(),
    membership.class_group_id
      ? c.env.DB.prepare(
          `SELECT COALESCE(SUM(ps.actual_activity_minutes), 0) AS pe_minutes
             FROM pe_sessions ps
             JOIN pe_timetable pt ON pt.id = ps.timetable_id
            WHERE pt.school_id = ?
              AND pt.class_group_id = ?
              AND ps.date = ?`
        ).bind(schoolId, membership.class_group_id, date).first()
      : Promise.resolve({ pe_minutes: 0 }),
    c.env.DB.prepare(
      `SELECT exercise_minutes, steps, active_energy_kcal
         FROM outside_school_activity_daily
        WHERE student_membership_id = ? AND date = ?`
    ).bind(membership.id, date).first(),
    c.env.DB.prepare(
      `SELECT
          COALESCE(SUM(duration_minutes), 0) AS total_minutes,
          COALESCE(SUM(CASE WHEN intensity = 'light' THEN duration_minutes ELSE 0 END), 0) AS light_minutes,
          COALESCE(SUM(CASE WHEN intensity = 'moderate' THEN duration_minutes ELSE 0 END), 0) AS moderate_minutes,
          COALESCE(SUM(CASE WHEN intensity = 'vigorous' THEN duration_minutes ELSE 0 END), 0) AS vigorous_minutes,
          COALESCE(SUM(estimated_active_energy_kcal), 0) AS estimated_active_energy_kcal
         FROM manual_activity_sessions
        WHERE student_membership_id = ? AND date = ?`
    ).bind(membership.id, date).first(),
    c.env.DB.prepare(
      `SELECT daily_energy_reference_kcal
         FROM student_preferences
        WHERE student_membership_id = ?`
    ).bind(membership.id).first(),
    c.env.DB.prepare(
      `SELECT daily_activity_target_minutes
         FROM schools
        WHERE id = ?`
    ).bind(schoolId).first()
  ]);

  const homeNutrition = {
    energyKcal: 0,
    proteinG: 0,
    fatG: 0,
    carbohydrateG: 0,
    fiberG: 0,
    sodiumMg: 0,
    sugarG: 0,
    saturatedFatG: 0
  };

  for (const row of (homeMeals as D1Result<Record<string, unknown>>).results) {
    try {
      const items = JSON.parse(String(row.confirmed_items_json)) as Array<{
        nutrition?: Record<string, number> | null;
      }>;
      for (const item of items) {
        const n = item.nutrition;
        if (!n) continue;
        homeNutrition.energyKcal += Number(n.energyKcal ?? 0);
        homeNutrition.proteinG += Number(n.proteinG ?? 0);
        homeNutrition.fatG += Number(n.fatG ?? 0);
        homeNutrition.carbohydrateG += Number(n.carbohydrateG ?? 0);
        homeNutrition.fiberG += Number(n.fiberG ?? 0);
        homeNutrition.sodiumMg += Number(n.sodiumMg ?? 0);
        homeNutrition.sugarG += Number(n.sugarG ?? 0);
        homeNutrition.saturatedFatG += Number(n.saturatedFatG ?? 0);
      }
    } catch {
      // Ignore malformed historical demo rows instead of breaking today's view.
    }
  }

  const energyKcal =
    Number((nutrition as any)?.energy_kcal ?? 0) + homeNutrition.energyKcal;
  const proteinG =
    Number((nutrition as any)?.protein_g ?? 0) + homeNutrition.proteinG;
  const fatG =
    Number((nutrition as any)?.fat_g ?? 0) + homeNutrition.fatG;
  const carbohydrateG =
    Number((nutrition as any)?.carbohydrate_g ?? 0) +
    homeNutrition.carbohydrateG;
  const fiberG =
    Number((nutrition as any)?.fiber_g ?? 0) + homeNutrition.fiberG;
  const sodiumMg =
    Number((nutrition as any)?.sodium_mg ?? 0) + homeNutrition.sodiumMg;
  const sugarG =
    Number((nutrition as any)?.sugar_g ?? 0) + homeNutrition.sugarG;
  const saturatedFatG =
    Number((nutrition as any)?.saturated_fat_g ?? 0) +
    homeNutrition.saturatedFatG;

  const macroEnergy = proteinG * 4 + fatG * 9 + carbohydrateG * 4;

  const healthMinutes = Number((health as any)?.exercise_minutes ?? 0);
  const manualMinutes = Number((manual as any)?.total_minutes ?? 0);
  // Manual sessions are a fallback/correction source. max() avoids obvious double counting.
  const outsideMinutes = Math.max(healthMinutes, manualMinutes);
  const peMinutes = Number((pe as any)?.pe_minutes ?? 0);
  const totalMinutes = peMinutes + outsideMinutes;
  const targetMinutes = Number(
    (school as any)?.daily_activity_target_minutes ?? 120
  );

  const dailyEnergyReferenceKcal =
    (preference as any)?.daily_energy_reference_kcal == null
      ? null
      : Number((preference as any).daily_energy_reference_kcal);

  return c.json({
    date,
    nutrition: {
      energyKcal,
      proteinG,
      fatG,
      carbohydrateG,
      fiberG,
      sodiumMg,
      sugarG,
      saturatedFatG,
      macroCompositionPercent: {
        protein: macroEnergy > 0 ? (proteinG * 4 / macroEnergy) * 100 : 0,
        fat: macroEnergy > 0 ? (fatG * 9 / macroEnergy) * 100 : 0,
        carbohydrate:
          macroEnergy > 0 ? (carbohydrateG * 4 / macroEnergy) * 100 : 0
      }
    },
    activity: {
      peMinutes,
      healthConnectOutsideMinutes: healthMinutes,
      manualOutsideMinutes: manualMinutes,
      outsideMinutes,
      totalMinutes,
      targetMinutes,
      targetReached: totalMinutes >= targetMinutes,
      intensityMinutes: {
        light: Number((manual as any)?.light_minutes ?? 0),
        moderate: Number((manual as any)?.moderate_minutes ?? 0),
        vigorous: Number((manual as any)?.vigorous_minutes ?? 0)
      },
      activeEnergyKcal:
        (health as any)?.active_energy_kcal == null
          ? null
          : Number((health as any).active_energy_kcal),
      manuallyEstimatedActiveEnergyKcal: Number(
        (manual as any)?.estimated_active_energy_kcal ?? 0
      )
    },
    energy: {
      dailyEnergyReferenceKcal,
      intakeKcal: energyKcal,
      referenceGapKcal:
        dailyEnergyReferenceKcal == null
          ? null
          : dailyEnergyReferenceKcal - energyKcal
    }
  });
});

app.post(
  "/v1/home-meals",
  zValidator("json", confirmedHomeMealSchema),
  async (c) => {
    const body = c.req.valid("json");
    const membership = await membershipFor(
      c.env.DB,
      c.get("schoolId"),
      c.get("participantId")
    );
    if (!membership) return c.json({ error: "membership_not_found" }, 404);

    const id = crypto.randomUUID();
    await c.env.DB.prepare(
      `INSERT INTO home_meals
         (id, student_membership_id, date, meal_slot, confirmed_items_json)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(
      id,
      membership.id,
      body.date,
      body.mealSlot,
      JSON.stringify(body.items)
    ).run();

    return c.json({ ok: true, id });
  }
);

app.post("/v1/home-meals/analyze", async (c) => {
  const contentType = c.req.header("content-type") ?? "";
  if (!contentType.includes("multipart/form-data")) {
    return c.json({ error: "multipart_required" }, 415);
  }

  const form = await c.req.raw.formData();
  const image = form.get("image");
  if (!(image instanceof File)) {
    return c.json({ error: "image_required" }, 400);
  }

  if (image.size > 8 * 1024 * 1024) {
    return c.json({ error: "image_too_large" }, 413);
  }

  if (!image.type.startsWith("image/")) {
    return c.json({ error: "invalid_image_type" }, 415);
  }

  const outbound = new FormData();
  outbound.set("image", image, image.name || "meal.jpg");
  outbound.set("prompt", HOME_MEAL_AGY_PROMPT);
  outbound.set("schemaVersion", "1");

  const response = await fetch(c.env.AGY_TASK_URL, {
    method: "POST",
    headers: c.env.AGY_TASK_TOKEN
      ? { Authorization: `Bearer ${c.env.AGY_TASK_TOKEN}` }
      : undefined,
    body: outbound
  });

  if (!response.ok) {
    return c.json(
      { error: "agy_failed", status: response.status },
      502
    );
  }

  const raw = await response.json();
  const parsed = homeMealAnalysisResultSchema.safeParse(raw);
  if (!parsed.success) {
    return c.json(
      {
        error: "agy_schema_invalid",
        issues: parsed.error.issues.slice(0, 8)
      },
      502
    );
  }

  return c.json(parsed.data);
});

app.get("/v1/admin/school/day-windows", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);

  const rows = await c.env.DB.prepare(
    `SELECT id, weekday, start_time, end_time
       FROM school_day_windows
      WHERE school_id = ?
      ORDER BY weekday, start_time`
  ).bind(c.get("schoolId")).all();

  return c.json({
    items: rows.results.map((row) => ({
      id: String(row.id),
      weekday: Number(row.weekday),
      startTime: String(row.start_time),
      endTime: String(row.end_time)
    }))
  });
});

app.put(
  "/v1/admin/school/day-windows",
  zValidator("json", adminSchoolDayWindowSchema),
  async (c) => {
    if (!requireAdmin(c.get("role"))) {
      return c.json({ error: "forbidden" }, 403);
    }

    const body = c.req.valid("json");
    const existing = await c.env.DB.prepare(
      `SELECT id
         FROM school_day_windows
        WHERE school_id = ?
          AND weekday = ?
          AND start_time = ?
          AND end_time = ?
        LIMIT 1`
    ).bind(
      c.get("schoolId"),
      body.weekday,
      body.startTime,
      body.endTime
    ).first<{ id: string }>();

    if (existing) return c.json({ ok: true, id: existing.id });

    const id = crypto.randomUUID();
    await c.env.DB.prepare(
      `INSERT INTO school_day_windows
         (id, school_id, weekday, start_time, end_time)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(
      id,
      c.get("schoolId"),
      body.weekday,
      body.startTime,
      body.endTime
    ).run();

    return c.json({ ok: true, id });
  }
);

app.get("/v1/admin/classes", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);

  const rows = await c.env.DB.prepare(
    `SELECT id, name
       FROM class_groups
      WHERE school_id = ?
      ORDER BY name`
  ).bind(c.get("schoolId")).all();

  return c.json({
    classes: rows.results.map((row) => ({
      id: String(row.id),
      name: String(row.name)
    }))
  });
});

app.get("/v1/admin/menus", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const rows = await c.env.DB.prepare(
    `SELECT m.id AS menu_id, m.meal_slot, d.id, d.name, d.standard_serving_grams,
            d.nutrition_per_serving_json, d.sort_order
       FROM menus m
       LEFT JOIN dishes d ON d.menu_id = m.id
      WHERE m.school_id = ? AND m.date = ?
      ORDER BY m.meal_slot, d.sort_order, d.name`
  ).bind(c.get("schoolId"), date).all();

  const menus = new Map<
    string,
    { id: string; mealSlot: string; dishes: unknown[] }
  >();

  for (const row of rows.results) {
    const menuId = String(row.menu_id);
    const menu = menus.get(menuId) ?? {
      id: menuId,
      mealSlot: String(row.meal_slot),
      dishes: []
    };
    if (row.id) {
      menu.dishes.push({
        id: String(row.id),
        name: String(row.name),
        standardServingGrams:
          row.standard_serving_grams == null
            ? null
            : Number(row.standard_serving_grams),
        nutritionPerServing: row.nutrition_per_serving_json
          ? JSON.parse(String(row.nutrition_per_serving_json))
          : null
      });
    }
    menus.set(menuId, menu);
  }

  return c.json({ date, menus: [...menus.values()] });
});

app.put(
  "/v1/admin/menus",
  zValidator("json", adminUpsertMenuSchema),
  async (c) => {
    if (!requireAdmin(c.get("role"))) {
      return c.json({ error: "forbidden" }, 403);
    }

    const body = c.req.valid("json");
    const schoolId = c.get("schoolId");

    let menu = await c.env.DB.prepare(
      "SELECT id FROM menus WHERE school_id = ? AND date = ? AND meal_slot = ? LIMIT 1"
    ).bind(
      schoolId,
      body.date,
      body.mealSlot
    ).first<{ id: string }>();

    if (!menu) {
      const id = crypto.randomUUID();
      await c.env.DB.prepare(
        "INSERT INTO menus (id, school_id, date, meal_slot) VALUES (?, ?, ?, ?)"
      ).bind(id, schoolId, body.date, body.mealSlot).run();
      menu = { id };
    }

    const statements = [
      c.env.DB.prepare("DELETE FROM dishes WHERE menu_id = ?").bind(menu.id),
      ...body.dishes.map((dish, index) =>
        c.env.DB.prepare(
          `INSERT INTO dishes
             (id, menu_id, name, standard_serving_grams,
              nutrition_per_serving_json, sort_order)
           VALUES (?, ?, ?, ?, ?, ?)`
        ).bind(
          crypto.randomUUID(),
          menu!.id,
          dish.name,
          dish.standardServingGrams,
          dish.nutritionPerServing
            ? JSON.stringify(dish.nutritionPerServing)
            : null,
          index
        )
      )
    ];

    await c.env.DB.batch(statements);
    return c.json({ ok: true, menuId: menu.id });
  }
);

app.get("/v1/admin/pe/timetable", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);
  const classGroupId = c.req.query("classGroupId");
  if (!classGroupId) return c.json({ error: "class_group_required" }, 400);

  const rows = await c.env.DB.prepare(
    `SELECT pt.id, pt.weekday, pt.start_time, pt.end_time
       FROM pe_timetable pt
       JOIN class_groups cg ON cg.id = pt.class_group_id
      WHERE pt.school_id = ?
        AND pt.class_group_id = ?
        AND cg.school_id = ?
      ORDER BY pt.weekday, pt.start_time`
  ).bind(
    c.get("schoolId"),
    classGroupId,
    c.get("schoolId")
  ).all();

  return c.json({
    classGroupId,
    items: rows.results.map((row) => ({
      id: String(row.id),
      weekday: Number(row.weekday),
      startTime: String(row.start_time),
      endTime: String(row.end_time)
    }))
  });
});

app.put(
  "/v1/admin/pe/timetable",
  zValidator("json", adminPeTimetableSchema),
  async (c) => {
    if (!requireAdmin(c.get("role"))) {
      return c.json({ error: "forbidden" }, 403);
    }

    const body = c.req.valid("json");
    const schoolId = c.get("schoolId");

    const classExists = await c.env.DB.prepare(
      `SELECT id FROM class_groups
        WHERE id = ? AND school_id = ?
        LIMIT 1`
    ).bind(body.classGroupId, schoolId).first();

    if (!classExists) {
      return c.json({ error: "class_group_not_found" }, 404);
    }

    const existing = await c.env.DB.prepare(
      `SELECT id
         FROM pe_timetable
        WHERE school_id = ?
          AND class_group_id = ?
          AND weekday = ?
          AND start_time = ?
          AND end_time = ?
        LIMIT 1`
    ).bind(
      schoolId,
      body.classGroupId,
      body.weekday,
      body.startTime,
      body.endTime
    ).first<{ id: string }>();

    if (existing) return c.json({ ok: true, id: existing.id });

    const id = crypto.randomUUID();
    await c.env.DB.prepare(
      `INSERT INTO pe_timetable
         (id, school_id, class_group_id, weekday, start_time, end_time)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(
      id,
      schoolId,
      body.classGroupId,
      body.weekday,
      body.startTime,
      body.endTime
    ).run();

    return c.json({ ok: true, id });
  }
);

app.put(
  "/v1/admin/pe/session",
  zValidator("json", adminPeSessionSchema),
  async (c) => {
    if (!requireAdmin(c.get("role"))) {
      return c.json({ error: "forbidden" }, 403);
    }

    const body = c.req.valid("json");
    const timetable = await c.env.DB.prepare(
      `SELECT pt.id
         FROM pe_timetable pt
        WHERE pt.id = ? AND pt.school_id = ?
        LIMIT 1`
    ).bind(
      body.timetableId,
      c.get("schoolId")
    ).first<{ id: string }>();

    if (!timetable) return c.json({ error: "timetable_not_found" }, 404);

    await c.env.DB.prepare(
      `INSERT INTO pe_sessions
         (id, timetable_id, date, actual_activity_minutes, recorded_by_admin_id, updated_at)
       VALUES (?, ?, ?, ?, 'demo-admin', datetime('now'))
       ON CONFLICT(timetable_id, date)
       DO UPDATE SET actual_activity_minutes = excluded.actual_activity_minutes,
                     recorded_by_admin_id = excluded.recorded_by_admin_id,
                     updated_at = excluded.updated_at`
    ).bind(
      crypto.randomUUID(),
      timetable.id,
      body.date,
      body.actualActivityMinutes
    ).run();

    return c.json({ ok: true });
  }
);

app.get("/v1/admin/pe/sessions", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);

  const date = c.req.query("date");
  const classGroupId = c.req.query("classGroupId");
  if (!date || !classGroupId) {
    return c.json({ error: "date_and_class_group_required" }, 400);
  }

  const weekday = weekdayFromDate(date);
  if (weekday == null) {
    return c.json({ error: "invalid_date" }, 400);
  }

  const rows = await c.env.DB.prepare(
    `SELECT pt.id AS timetable_id, pt.weekday, pt.start_time, pt.end_time,
            ps.actual_activity_minutes
       FROM pe_timetable pt
       LEFT JOIN pe_sessions ps
         ON ps.timetable_id = pt.id AND ps.date = ?
      WHERE pt.school_id = ?
        AND pt.class_group_id = ?
        AND pt.weekday = ?
      ORDER BY pt.start_time`
  ).bind(
    date,
    c.get("schoolId"),
    classGroupId,
    weekday
  ).all();

  return c.json({
    date,
    classGroupId,
    items: rows.results.map((row) => ({
      timetableId: String(row.timetable_id),
      weekday: Number(row.weekday),
      startTime: String(row.start_time),
      endTime: String(row.end_time),
      actualActivityMinutes:
        row.actual_activity_minutes == null
          ? null
          : Number(row.actual_activity_minutes)
    }))
  });
});

app.get("/v1/admin/stats/overview", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const schoolId = c.get("schoolId");

  const [membershipCount, meal, nutrition, pe, activity, activityGoal] =
    await Promise.all([
    c.env.DB.prepare(
      "SELECT COUNT(*) AS count FROM student_memberships WHERE school_id = ?"
    ).bind(schoolId).first<{ count: number }>(),
    c.env.DB.prepare(
      `SELECT COUNT(DISTINCT sm.id) AS participants,
              AVG(mc.serving_multiplier) AS avg_serving_multiplier,
              AVG(mc.consumed_grams) AS avg_consumed_grams
         FROM meal_consumption mc
         JOIN student_memberships sm ON sm.id = mc.student_membership_id
         JOIN dishes d ON d.id = mc.dish_id
         JOIN menus m ON m.id = d.menu_id
        WHERE sm.school_id = ? AND m.date = ?`
    ).bind(schoolId, date).first(),
    c.env.DB.prepare(
      `SELECT
          AVG(student_energy) AS avg_energy_kcal,
          AVG(student_protein) AS avg_protein_g,
          AVG(student_fat) AS avg_fat_g,
          AVG(student_carbs) AS avg_carbohydrate_g
       FROM (
         SELECT sm.id,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.energyKcal'), 0) * mc.serving_multiplier) AS student_energy,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.proteinG'), 0) * mc.serving_multiplier) AS student_protein,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.fatG'), 0) * mc.serving_multiplier) AS student_fat,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.carbohydrateG'), 0) * mc.serving_multiplier) AS student_carbs
         FROM student_memberships sm
         JOIN meal_consumption mc ON mc.student_membership_id = sm.id
         JOIN dishes d ON d.id = mc.dish_id
         JOIN menus m ON m.id = d.menu_id
         WHERE sm.school_id = ? AND m.date = ?
         GROUP BY sm.id
       )`
    ).bind(schoolId, date).first(),
    c.env.DB.prepare(
      `SELECT AVG(ps.actual_activity_minutes) AS avg_pe_minutes,
              COUNT(*) AS recorded_sessions
         FROM pe_sessions ps
         JOIN pe_timetable pt ON pt.id = ps.timetable_id
        WHERE pt.school_id = ? AND ps.date = ?`
    ).bind(schoolId, date).first(),
    c.env.DB.prepare(
      `SELECT AVG(a.exercise_minutes) AS avg_outside_minutes,
              AVG(a.active_energy_kcal) AS avg_active_energy_kcal
         FROM outside_school_activity_daily a
         JOIN student_memberships sm ON sm.id = a.student_membership_id
        WHERE sm.school_id = ? AND a.date = ?`
    ).bind(schoolId, date).first(),
    c.env.DB.prepare(
      `WITH
         pe_by_class AS (
           SELECT pt.class_group_id,
                  SUM(ps.actual_activity_minutes) AS pe_minutes
             FROM pe_sessions ps
             JOIN pe_timetable pt ON pt.id = ps.timetable_id
            WHERE ps.date = ? AND pt.school_id = ?
            GROUP BY pt.class_group_id
         ),
         manual_by_student AS (
           SELECT student_membership_id,
                  SUM(duration_minutes) AS manual_minutes
             FROM manual_activity_sessions
            WHERE date = ?
            GROUP BY student_membership_id
         ),
         phone_by_student AS (
           SELECT student_membership_id,
                  exercise_minutes AS phone_minutes
             FROM outside_school_activity_daily
            WHERE date = ?
         ),
         totals AS (
           SELECT sm.id,
                  s.daily_activity_target_minutes AS target_minutes,
                  COALESCE(pe.pe_minutes, 0) +
                    MAX(
                      COALESCE(phone.phone_minutes, 0),
                      COALESCE(manual.manual_minutes, 0)
                    ) AS total_minutes
             FROM student_memberships sm
             JOIN schools s ON s.id = sm.school_id
             LEFT JOIN pe_by_class pe
               ON pe.class_group_id = sm.class_group_id
             LEFT JOIN manual_by_student manual
               ON manual.student_membership_id = sm.id
             LEFT JOIN phone_by_student phone
               ON phone.student_membership_id = sm.id
            WHERE sm.school_id = ?
         )
         SELECT AVG(total_minutes) AS avg_total_minutes,
                AVG(
                  CASE WHEN total_minutes >= target_minutes
                       THEN 1.0 ELSE 0.0 END
                ) AS target_completion_rate
           FROM totals`
    ).bind(date, schoolId, date, date, schoolId).first()
  ]);

  const total = Number(membershipCount?.count ?? 0);
  const participants = Number((meal as any)?.participants ?? 0);

  return c.json({
    date,
    meal: {
      ...(meal ?? {}),
      participationRate: total === 0 ? 0 : participants / total
    },
    nutrition,
    pe,
    activity: {
      ...(activity ?? {}),
      ...(activityGoal ?? {})
    }
  });
});

export default app;
