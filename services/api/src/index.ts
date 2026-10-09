import { Hono, type Context } from "hono";
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
  schoolActivityOverrideSchema,
  recordMealSchema,
  todayMenuSchema
} from "@health2609/contracts";
import {
  syncChangePasswordSchema,
  syncLoginSchema,
  syncPushSchema,
  syncRecoveryResetPasswordSchema,
  syncRegisterSchema,
  syncRotateKeySchema
} from "@health2609/contracts/sync";
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

app.get("/", (c) => c.json({
  service: "health2609-api",
  status: "ok",
  demo: true
}));

app.get("/healthz", (c) => c.json({ ok: true }));

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

app.get("/v1/student/schools", async (c) => {
  const participantId = c.get("participantId");
  const rows = await c.env.DB.prepare(
    `SELECT s.id, s.name, s.timezone
       FROM student_memberships sm
       JOIN schools s ON s.id = sm.school_id
      WHERE sm.participant_id = ?
      ORDER BY s.name`
  ).bind(participantId).all();

  return c.json({
    schools: rows.results.map((row) => ({
      id: String(row.id),
      name: String(row.name),
      timezone: String(row.timezone)
    }))
  });
});

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

app.get("/v1/today/school-activity", async (c) => {
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const weekday = weekdayFromDate(date);
  if (weekday == null) return c.json({ error: "invalid_date" }, 400);

  const membership = await membershipFor(
    c.env.DB,
    c.get("schoolId"),
    c.get("participantId")
  );
  if (!membership) return c.json({ error: "membership_not_found" }, 404);

  const [schoolWindows, peRows, override] = await Promise.all([
    c.env.DB.prepare(
      `SELECT id, start_time, end_time
         FROM school_day_windows
        WHERE school_id = ? AND weekday = ?
        ORDER BY start_time`
    ).bind(c.get("schoolId"), weekday).all(),
    membership.class_group_id
      ? c.env.DB.prepare(
          `SELECT pt.id, pt.start_time, pt.end_time, ps.actual_activity_minutes
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
          membership.class_group_id,
          weekday
        ).all()
      : Promise.resolve({ results: [] }),
    c.env.DB.prepare(
      `SELECT source, exercise_minutes, steps, active_energy_kcal
         FROM student_school_activity_overrides
        WHERE student_membership_id = ? AND date = ?`
    ).bind(membership.id, date).first()
  ]);

  const peWindows = (peRows as D1Result<Record<string, unknown>>).results.map((row) => ({
    id: String(row.id),
    startTime: String(row.start_time),
    endTime: String(row.end_time),
    schoolRecordedMinutes:
      row.actual_activity_minutes == null
        ? null
        : Number(row.actual_activity_minutes)
  }));
  const schoolRecordedMinutes = peWindows.reduce(
    (sum, row) => sum + (row.schoolRecordedMinutes ?? 0),
    0
  );

  return c.json({
    date,
    weekday,
    schoolDayWindows: schoolWindows.results.map((row) => ({
      id: String(row.id),
      startTime: String(row.start_time),
      endTime: String(row.end_time)
    })),
    peWindows,
    schoolRecordedMinutes,
    selectedSource: override?.source === "health_connect" ? "health_connect" : "school",
    wearableMinutes:
      override?.exercise_minutes == null ? null : Number(override.exercise_minutes),
    wearableSteps:
      override?.steps == null ? null : Number(override.steps),
    wearableActiveEnergyKcal:
      override?.active_energy_kcal == null
        ? null
        : Number(override.active_energy_kcal)
  });
});

app.put(
  "/v1/activity/school-source",
  zValidator("json", schoolActivityOverrideSchema),
  async (c) => {
    const body = c.req.valid("json");
    const membership = await membershipFor(
      c.env.DB,
      c.get("schoolId"),
      c.get("participantId")
    );
    if (!membership) return c.json({ error: "membership_not_found" }, 404);

    await c.env.DB.prepare(
      `INSERT INTO student_school_activity_overrides
         (student_membership_id, date, source, exercise_minutes, steps,
          active_energy_kcal, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, datetime('now'))
       ON CONFLICT(student_membership_id, date)
       DO UPDATE SET source = excluded.source,
                     exercise_minutes = excluded.exercise_minutes,
                     steps = excluded.steps,
                     active_energy_kcal = excluded.active_energy_kcal,
                     updated_at = excluded.updated_at`
    ).bind(
      membership.id,
      body.date,
      body.source,
      body.source === "health_connect" ? body.exerciseMinutes ?? null : null,
      body.source === "health_connect" ? body.steps ?? null : null,
      body.source === "health_connect" ? body.activeEnergyKcal ?? null : null
    ).run();

    return c.json({ ok: true });
  }
);

app.get("/v1/today/menu", async (c) => {
  const date = c.req.query("date");
  const mealSlot = c.req.query("mealSlot") ?? "lunch";
  if (!date) return c.json({ error: "date_required" }, 400);

  const rows = await c.env.DB.prepare(
    `SELECT d.id, d.name, d.standard_serving_grams, d.nutrition_per_serving_json
       FROM menus m
       JOIN dishes d ON d.menu_id = m.id
      WHERE m.school_id = ? AND m.date = ? AND m.meal_slot = ?
        AND d.active = 1
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
          AND d.active = 1
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

  const [nutrition, homeMeals, pe, health, manual, preference, school, schoolActivityOverride] = await Promise.all([
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
    ).bind(schoolId).first(),
    c.env.DB.prepare(
      `SELECT source, exercise_minutes, active_energy_kcal
         FROM student_school_activity_overrides
        WHERE student_membership_id = ? AND date = ?`
    ).bind(membership.id, date).first()
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
  const schoolRecordedPeMinutes = Number((pe as any)?.pe_minutes ?? 0);
  const schoolPeSource =
    (schoolActivityOverride as any)?.source === "health_connect"
      ? "health_connect"
      : "school";
  const healthConnectSchoolMinutes =
    (schoolActivityOverride as any)?.exercise_minutes == null
      ? null
      : Number((schoolActivityOverride as any).exercise_minutes);
  const peMinutes =
    schoolPeSource === "health_connect" && healthConnectSchoolMinutes != null
      ? healthConnectSchoolMinutes
      : schoolRecordedPeMinutes;
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
      schoolRecordedPeMinutes,
      schoolPeSource,
      healthConnectSchoolMinutes,
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
      ),
      steps:
        (health as any)?.steps == null
          ? null
          : Number((health as any).steps)
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
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(student_membership_id, date, meal_slot)
       DO UPDATE SET
         confirmed_items_json = excluded.confirmed_items_json,
         created_at = datetime('now')`
    ).bind(
      id,
      membership.id,
      body.date,
      body.mealSlot,
      JSON.stringify(body.items)
    ).run();

    const saved = await c.env.DB.prepare(
      `SELECT id
         FROM home_meals
        WHERE student_membership_id = ? AND date = ? AND meal_slot = ?
        LIMIT 1`
    ).bind(membership.id, body.date, body.mealSlot).first<{ id: string }>();

    return c.json({ ok: true, id: saved?.id ?? id });
  }
);

app.post("/v1/home-meals/analyze", async (c) => {
  const membership = await membershipFor(
    c.env.DB,
    c.get("schoolId"),
    c.get("participantId")
  );
  if (!membership) return c.json({ error: "membership_not_found" }, 404);

  const contentType = c.req.header("content-type") ?? "";
  if (!contentType.includes("multipart/form-data")) {
    return c.json({ error: "multipart_required" }, 415);
  }

  const form = await c.req.raw.formData();
  const rawImages = [
    ...form.getAll("images"),
    ...form.getAll("image")
  ];
  const images = rawImages.filter((item): item is File => item instanceof File);

  if (images.length === 0) {
    return c.json({ error: "image_required" }, 400);
  }

  if (images.length > 5) {
    return c.json({ error: "too_many_images", message: "一次最多上传 5 张餐食图片" }, 400);
  }

  for (const image of images) {
    if (image.size <= 0 || image.size > 8 * 1024 * 1024) {
      return c.json({ error: "image_too_large", message: "单张图片大小不能超过 8 MB" }, 413);
    }
    if (!image.type.startsWith("image/")) {
      return c.json({ error: "invalid_image_type", message: "仅支持图片格式文件" }, 415);
    }
  }

  const requestId = c.req.header("x-request-id") || c.req.header("cf-ray") || crypto.randomUUID();
  c.header("X-Request-Id", requestId);

  const outbound = new FormData();
  images.forEach((img, idx) => {
    outbound.append("images", img, img.name || `meal_${idx}.jpg`);
  });
  outbound.set("prompt", HOME_MEAL_AGY_PROMPT);
  outbound.set("schemaVersion", "1");

  let response: Response | null = null;
  try {
    const headers: Record<string, string> = {
      "X-Request-Id": requestId
    };
    if (c.env.AGY_TASK_TOKEN) {
      headers["Authorization"] = `Bearer ${c.env.AGY_TASK_TOKEN}`;
    }

    response = await fetch(c.env.AGY_TASK_URL, {
      method: "POST",
      headers,
      body: outbound,
      signal: AbortSignal.timeout(135_000)
    });
  } catch (err: any) {
    if (err.name === "TimeoutError" || err.name === "AbortError") {
      return c.json(
        {
          error: "agy_timeout",
          requestId,
          message: "云端视觉分析超时（135秒），请重试或检查本地网桥"
        },
        504
      );
    }
    return c.json(
      {
        error: "agy_unreachable",
        requestId,
        message: err.message || "未能连接至云端视觉分析服务"
      },
      502
    );
  }

  if (!response.ok) {
    let bridgeError: any = null;
    try {
      bridgeError = await response.json();
    } catch {}

    const rawError = bridgeError?.error;
    const errorCode =
      (response.status === 401 || rawError === "unauthorized")
        ? "agy_auth_failed"
        : (rawError || (response.status === 429 ? "queue_full" : "agy_failed"));
    const detail = bridgeError?.detail || bridgeError?.message;
    const status = response.status === 429 ? 429 : response.status === 401 ? 502 : response.status;

    return c.json(
      {
        error: errorCode,
        requestId,
        status: response.status,
        detail,
        message: detail ? `视觉分析失败: ${detail}` : `视觉服务异常 (${response.status})`
      },
      status >= 400 && status <= 599 ? (status as any) : 502
    );
  }

  let raw: unknown;
  try {
    raw = await response.json();
  } catch {
    return c.json(
      {
        error: "agy_invalid_json",
        requestId,
        message: "未能解析视觉分析服务返回的 JSON 数据"
      },
      502
    );
  }

  const parsed = homeMealAnalysisResultSchema.safeParse(raw);
  if (!parsed.success) {
    return c.json(
      {
        error: "agy_schema_invalid",
        requestId,
        message: "视觉分析返回结果不符合规范定义",
        issues: parsed.error.issues.slice(0, 8)
      },
      502
    );
  }

  return c.json({
    ...parsed.data,
    requestId
  });
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
    const schoolId = c.get("schoolId");
    const id = crypto.randomUUID();

    await c.env.DB.batch([
      c.env.DB.prepare(
        `DELETE FROM school_day_windows
          WHERE school_id = ? AND weekday = ?`
      ).bind(schoolId, body.weekday),
      c.env.DB.prepare(
        `INSERT INTO school_day_windows
           (id, school_id, weekday, start_time, end_time)
         VALUES (?, ?, ?, ?, ?)`
      ).bind(
        id,
        schoolId,
        body.weekday,
        body.startTime,
        body.endTime
      )
    ]);

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
       LEFT JOIN dishes d ON d.menu_id = m.id AND d.active = 1
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

    const candidateMenuId = crypto.randomUUID();
    await c.env.DB.prepare(
      `INSERT INTO menus (id, school_id, date, meal_slot)
       VALUES (?, ?, ?, ?)
       ON CONFLICT(school_id, date, meal_slot) DO NOTHING`
    ).bind(
      candidateMenuId,
      schoolId,
      body.date,
      body.mealSlot
    ).run();

    const menu = await c.env.DB.prepare(
      "SELECT id FROM menus WHERE school_id = ? AND date = ? AND meal_slot = ? LIMIT 1"
    ).bind(
      schoolId,
      body.date,
      body.mealSlot
    ).first<{ id: string }>();

    if (!menu) {
      return c.json({ error: "menu_upsert_failed" }, 500);
    }

    const providedIds = body.dishes
      .map((dish) => dish.id)
      .filter((id): id is string => Boolean(id));

    if (new Set(providedIds).size !== providedIds.length) {
      return c.json({ error: "duplicate_dish_id" }, 400);
    }

    if (providedIds.length) {
      const placeholders = providedIds.map(() => "?").join(",");
      const owned = await c.env.DB.prepare(
        `SELECT id
           FROM dishes
          WHERE menu_id = ? AND id IN (${placeholders})`
      ).bind(menu.id, ...providedIds).all<{ id: string }>();

      if (owned.results.length !== providedIds.length) {
        return c.json({ error: "dish_scope_mismatch" }, 400);
      }
    }

    const statements: D1PreparedStatement[] = [
      c.env.DB.prepare(
        "UPDATE dishes SET active = 0 WHERE menu_id = ?"
      ).bind(menu.id)
    ];

    body.dishes.forEach((dish, index) => {
      const nutrition = dish.nutritionPerServing
        ? JSON.stringify(dish.nutritionPerServing)
        : null;

      if (dish.id) {
        statements.push(
          c.env.DB.prepare(
            `UPDATE dishes
                SET name = ?,
                    standard_serving_grams = ?,
                    nutrition_per_serving_json = ?,
                    sort_order = ?,
                    active = 1
              WHERE id = ? AND menu_id = ?`
          ).bind(
            dish.name,
            dish.standardServingGrams,
            nutrition,
            index,
            dish.id,
            menu!.id
          )
        );
      } else {
        statements.push(
          c.env.DB.prepare(
            `INSERT INTO dishes
               (id, menu_id, name, standard_serving_grams,
                nutrition_per_serving_json, sort_order, active)
             VALUES (?, ?, ?, ?, ?, ?, 1)`
          ).bind(
            crypto.randomUUID(),
            menu!.id,
            dish.name,
            dish.standardServingGrams,
            nutrition,
            index
          )
        );
      }
    });

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
      `SELECT pt.id, pt.weekday
         FROM pe_timetable pt
        WHERE pt.id = ? AND pt.school_id = ?
        LIMIT 1`
    ).bind(
      body.timetableId,
      c.get("schoolId")
    ).first<{ id: string; weekday: number }>();

    if (!timetable) return c.json({ error: "timetable_not_found" }, 404);

    if (weekdayFromDate(body.date) !== timetable.weekday) {
      return c.json(
        {
          error: "pe_date_weekday_mismatch",
          message: "PE session date does not match timetable weekday"
        },
        400
      );
    }

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
  const weekday = date ? weekdayFromDate(date) : null;
  if (!date || weekday === null) {
    return c.json({ error: "valid_date_required" }, 400);
  }

  const schoolId = c.get("schoolId");
  const classGroupId = c.req.query("classGroupId")?.trim() || null;

  if (classGroupId) {
    const ownedClass = await c.env.DB.prepare(
      "SELECT id FROM class_groups WHERE id = ? AND school_id = ? LIMIT 1"
    ).bind(classGroupId, schoolId).first<{ id: string }>();
    if (!ownedClass) return c.json({ error: "class_not_found" }, 404);
  }

  const [
    membershipCount,
    meal,
    nutrition,
    pe,
    activity,
    activityGoal,
    dishRows,
    mealTrend,
    activityTrend
  ] = await Promise.all([
    c.env.DB.prepare(
      `SELECT COUNT(*) AS count
         FROM student_memberships
        WHERE school_id = ?
          AND (? IS NULL OR class_group_id = ?)`
    ).bind(schoolId, classGroupId, classGroupId).first<{ count: number }>(),

    c.env.DB.prepare(
      `SELECT COUNT(DISTINCT sm.id) AS participants,
              AVG(mc.serving_multiplier) AS avg_serving_multiplier,
              AVG(mc.consumed_grams) AS avg_consumed_grams
         FROM meal_consumption mc
         JOIN student_memberships sm ON sm.id = mc.student_membership_id
         JOIN dishes d ON d.id = mc.dish_id
         JOIN menus m ON m.id = d.menu_id
        WHERE sm.school_id = ?
          AND m.date = ?
          AND (? IS NULL OR sm.class_group_id = ?)`
    ).bind(schoolId, date, classGroupId, classGroupId).first(),

    c.env.DB.prepare(
      `SELECT
          AVG(student_energy) AS avg_energy_kcal,
          AVG(student_protein) AS avg_protein_g,
          AVG(student_fat) AS avg_fat_g,
          AVG(student_carbs) AS avg_carbohydrate_g,
          AVG(student_fiber) AS avg_fiber_g,
          AVG(student_sodium) AS avg_sodium_mg,
          AVG(student_sugar) AS avg_sugar_g,
          AVG(student_saturated_fat) AS avg_saturated_fat_g
       FROM (
         SELECT sm.id,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.energyKcal'), 0) * mc.serving_multiplier) AS student_energy,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.proteinG'), 0) * mc.serving_multiplier) AS student_protein,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.fatG'), 0) * mc.serving_multiplier) AS student_fat,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.carbohydrateG'), 0) * mc.serving_multiplier) AS student_carbs,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.fiberG'), 0) * mc.serving_multiplier) AS student_fiber,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.sodiumMg'), 0) * mc.serving_multiplier) AS student_sodium,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.sugarG'), 0) * mc.serving_multiplier) AS student_sugar,
           SUM(COALESCE(json_extract(d.nutrition_per_serving_json, '$.saturatedFatG'), 0) * mc.serving_multiplier) AS student_saturated_fat
         FROM student_memberships sm
         JOIN meal_consumption mc ON mc.student_membership_id = sm.id
         JOIN dishes d ON d.id = mc.dish_id
         JOIN menus m ON m.id = d.menu_id
        WHERE sm.school_id = ?
          AND m.date = ?
          AND (? IS NULL OR sm.class_group_id = ?)
        GROUP BY sm.id
       )`
    ).bind(schoolId, date, classGroupId, classGroupId).first(),

    c.env.DB.prepare(
      `SELECT AVG(ps.actual_activity_minutes) AS avg_pe_minutes,
              COUNT(ps.id) AS recorded_sessions,
              COUNT(pt.id) AS scheduled_sessions
         FROM pe_timetable pt
         LEFT JOIN pe_sessions ps
           ON ps.timetable_id = pt.id AND ps.date = ?
        WHERE pt.school_id = ?
          AND pt.weekday = ?
          AND (? IS NULL OR pt.class_group_id = ?)`
    ).bind(date, schoolId, weekday, classGroupId, classGroupId).first(),

    c.env.DB.prepare(
      `SELECT AVG(a.active_energy_kcal) AS avg_active_energy_kcal
         FROM outside_school_activity_daily a
         JOIN student_memberships sm ON sm.id = a.student_membership_id
        WHERE sm.school_id = ?
          AND a.date = ?
          AND (? IS NULL OR sm.class_group_id = ?)`
    ).bind(schoolId, date, classGroupId, classGroupId).first(),

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
                  MAX(
                    COALESCE(phone.phone_minutes, 0),
                    COALESCE(manual.manual_minutes, 0)
                  ) AS outside_minutes,
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
              AND (? IS NULL OR sm.class_group_id = ?)
         )
         SELECT AVG(outside_minutes) AS avg_outside_minutes,
                AVG(total_minutes) AS avg_total_minutes,
                AVG(
                  CASE WHEN total_minutes >= target_minutes
                       THEN 1.0 ELSE 0.0 END
                ) AS target_completion_rate
           FROM totals`
    ).bind(
      date,
      schoolId,
      date,
      date,
      schoolId,
      classGroupId,
      classGroupId
    ).first(),

    c.env.DB.prepare(
      `SELECT d.id,
              d.name,
              d.standard_serving_grams,
              COUNT(DISTINCT sm.id) AS participants,
              AVG(CASE WHEN sm.id IS NOT NULL THEN mc.serving_multiplier END)
                AS avg_serving_multiplier,
              AVG(CASE WHEN sm.id IS NOT NULL THEN mc.consumed_grams END)
                AS avg_consumed_grams,
              AVG(
                CASE
                  WHEN sm.id IS NULL THEN NULL
                  WHEN d.standard_serving_grams > 0
                       AND mc.consumed_grams IS NOT NULL
                    THEN mc.consumed_grams / d.standard_serving_grams
                  ELSE mc.serving_multiplier
                END
              ) AS avg_completion
         FROM menus m
         JOIN dishes d ON d.menu_id = m.id
         LEFT JOIN meal_consumption mc ON mc.dish_id = d.id
         LEFT JOIN student_memberships sm
           ON sm.id = mc.student_membership_id
          AND sm.school_id = ?
          AND (? IS NULL OR sm.class_group_id = ?)
        WHERE m.school_id = ? AND m.date = ?
        GROUP BY d.id, d.name, d.standard_serving_grams, d.sort_order, d.active
       HAVING d.active = 1 OR COUNT(DISTINCT sm.id) > 0
        ORDER BY d.sort_order, d.name`
    ).bind(
      schoolId,
      classGroupId,
      classGroupId,
      schoolId,
      date
    ).all(),

    c.env.DB.prepare(
      `WITH RECURSIVE days(day) AS (
         SELECT date(?, '-6 day')
         UNION ALL
         SELECT date(day, '+1 day') FROM days WHERE day < date(?)
       )
       SELECT days.day AS date,
              COUNT(DISTINCT sm.id) AS participants
         FROM days
         LEFT JOIN menus m
           ON m.school_id = ? AND m.date = days.day
         LEFT JOIN dishes d ON d.menu_id = m.id
         LEFT JOIN meal_consumption mc ON mc.dish_id = d.id
         LEFT JOIN student_memberships sm
           ON sm.id = mc.student_membership_id
          AND sm.school_id = ?
          AND (? IS NULL OR sm.class_group_id = ?)
        GROUP BY days.day
        ORDER BY days.day`
    ).bind(
      date,
      date,
      schoolId,
      schoolId,
      classGroupId,
      classGroupId
    ).all(),

    c.env.DB.prepare(
      `WITH RECURSIVE days(day) AS (
         SELECT date(?, '-6 day')
         UNION ALL
         SELECT date(day, '+1 day') FROM days WHERE day < date(?)
       ),
       members AS (
         SELECT sm.id,
                sm.class_group_id,
                s.daily_activity_target_minutes AS target_minutes
           FROM student_memberships sm
           JOIN schools s ON s.id = sm.school_id
          WHERE sm.school_id = ?
            AND (? IS NULL OR sm.class_group_id = ?)
       ),
       pe_by_class AS (
         SELECT ps.date,
                pt.class_group_id,
                SUM(ps.actual_activity_minutes) AS pe_minutes
           FROM pe_sessions ps
           JOIN pe_timetable pt ON pt.id = ps.timetable_id
          WHERE pt.school_id = ?
            AND ps.date BETWEEN date(?, '-6 day') AND date(?)
          GROUP BY ps.date, pt.class_group_id
       ),
       manual_by_student AS (
         SELECT date,
                student_membership_id,
                SUM(duration_minutes) AS manual_minutes
           FROM manual_activity_sessions
          WHERE date BETWEEN date(?, '-6 day') AND date(?)
          GROUP BY date, student_membership_id
       ),
       phone_by_student AS (
         SELECT date,
                student_membership_id,
                exercise_minutes AS phone_minutes
           FROM outside_school_activity_daily
          WHERE date BETWEEN date(?, '-6 day') AND date(?)
       ),
       totals AS (
         SELECT days.day AS date,
                members.id,
                members.target_minutes,
                COALESCE(pe.pe_minutes, 0) +
                  MAX(
                    COALESCE(phone.phone_minutes, 0),
                    COALESCE(manual.manual_minutes, 0)
                  ) AS total_minutes
           FROM days
           CROSS JOIN members
           LEFT JOIN pe_by_class pe
             ON pe.date = days.day
            AND pe.class_group_id = members.class_group_id
           LEFT JOIN manual_by_student manual
             ON manual.date = days.day
            AND manual.student_membership_id = members.id
           LEFT JOIN phone_by_student phone
             ON phone.date = days.day
            AND phone.student_membership_id = members.id
       )
       SELECT date,
              AVG(total_minutes) AS avg_total_minutes,
              AVG(
                CASE WHEN total_minutes >= target_minutes
                     THEN 1.0 ELSE 0.0 END
              ) AS target_completion_rate
         FROM totals
        GROUP BY date
        ORDER BY date`
    ).bind(
      date,
      date,
      schoolId,
      classGroupId,
      classGroupId,
      schoolId,
      date,
      date,
      date,
      date,
      date,
      date
    ).all()
  ]);

  const totalStudents = Number(membershipCount?.count ?? 0);
  const participants = Number((meal as any)?.participants ?? 0);
  const totalEatenStudents = participants;
  const recordedSessions = Number((pe as any)?.recorded_sessions ?? 0);
  const scheduledSessions = Number((pe as any)?.scheduled_sessions ?? 0);

  const privacyMasked = totalStudents < 3 || totalEatenStudents < 3;
  const maskedNutrition = privacyMasked
    ? {
        avg_energy_kcal: null,
        avg_protein_g: null,
        avg_fat_g: null,
        avg_carbohydrate_g: null,
        avg_fiber_g: null,
        avg_sodium_mg: null,
        avg_sugar_g: null,
        avg_saturated_fat_g: null
      }
    : nutrition;

  const activityTrendByDate = new Map(
    activityTrend.results.map((row) => [
      String(row.date),
      {
        avgTotalMinutes: Number(row.avg_total_minutes ?? 0),
        targetCompletionRate: Number(row.target_completion_rate ?? 0)
      }
    ])
  );

  const trend = mealTrend.results.map((row) => {
    const pointDate = String(row.date);
    const activityPoint = activityTrendByDate.get(pointDate);
    const dayParticipants = Number(row.participants ?? 0);

    return {
      date: pointDate,
      mealParticipationRate:
        totalStudents === 0 ? 0 : dayParticipants / totalStudents,
      avgTotalMinutes: activityPoint?.avgTotalMinutes ?? 0,
      targetCompletionRate: activityPoint?.targetCompletionRate ?? 0
    };
  });

  return c.json({
    date,
    classGroupId,
    totalStudents,
    privacyMasked,
    meal: {
      ...(meal ?? {}),
      total_eaten_students: totalEatenStudents,
      participationRate:
        totalStudents === 0 ? 0 : participants / totalStudents
    },
    participation: {
      total_eaten_students: totalEatenStudents,
      participationRate:
        totalStudents === 0 ? 0 : participants / totalStudents
    },
    nutrition: maskedNutrition,
    pe: {
      ...(pe ?? {}),
      recordCoverage:
        scheduledSessions === 0 ? 0 : recordedSessions / scheduledSessions
    },
    activity: {
      ...(activity ?? {}),
      ...(activityGoal ?? {})
    },
    dishes: dishRows.results.map((row) => ({
      id: String(row.id),
      name: String(row.name),
      standardServingGrams:
        row.standard_serving_grams == null
          ? null
          : Number(row.standard_serving_grams),
      participants: Number(row.participants ?? 0),
      participationRate:
        totalStudents === 0
          ? 0
          : Number(row.participants ?? 0) / totalStudents,
      avgServingMultiplier: Number(row.avg_serving_multiplier ?? 0),
      avgConsumedGrams:
        row.avg_consumed_grams == null
          ? null
          : Number(row.avg_consumed_grams),
      avgCompletion: Number(row.avg_completion ?? 0)
    })),
    trend
  });
});

/* -------------------------------------------------------------------------- */
/*                Multi-device E2EE Sync v2 (authenticated D1)                 */
/* -------------------------------------------------------------------------- */

type SyncAuth = {
  userId: string;
  deviceId: string;
};

function syncTokenFromRequest(c: Context<AppEnv>): string | null {
  const authorization = c.req.header("Authorization") || "";
  if (authorization.startsWith("Bearer ")) {
    return authorization.slice(7).trim() || null;
  }
  return c.req.header("x-sync-token") || null;
}

async function sha256Base64(value: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value)
  );
  const bytes = new Uint8Array(digest);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

function randomSyncToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

async function authenticateSync(c: Context<AppEnv>): Promise<SyncAuth | null> {
  const token = syncTokenFromRequest(c);
  if (!token) return null;
  const tokenHash = await sha256Base64(token);
  const row = await c.env.DB.prepare(
    `SELECT id, user_id
       FROM sync_devices_v2
      WHERE token_hash = ? AND revoked_at IS NULL
      LIMIT 1`
  ).bind(tokenHash).first<{ id: string; user_id: string }>();
  if (!row) return null;

  await c.env.DB.prepare(
    "UPDATE sync_devices_v2 SET last_seen_at = datetime('now') WHERE id = ?"
  ).bind(row.id).run();

  return { userId: row.user_id, deviceId: row.id };
}

async function syncKeyEnvelopes(db: D1Database, userId: string, kind: "password" | "recovery") {
  const rows = await db.prepare(
    `SELECT epoch, password_wrapped_key, password_nonce,
            recovery_wrapped_key, recovery_nonce
       FROM sync_key_epochs_v2
      WHERE user_id = ?
      ORDER BY epoch ASC`
  ).bind(userId).all();

  return rows.results.map((row) => kind === "password"
    ? {
        epoch: Number(row.epoch),
        wrappedKey: String(row.password_wrapped_key),
        nonce: String(row.password_nonce)
      }
    : {
        epoch: Number(row.epoch),
        wrappedKey: String(row.recovery_wrapped_key),
        nonce: String(row.recovery_nonce)
      }
  );
}

app.get("/v1/sync/auth/challenge", async (c) => {
  const username = (c.req.query("username") || "").trim().toLowerCase();
  if (!username) return c.json({ error: "missing_username" }, 400);

  const account = await c.env.DB.prepare(
    `SELECT password_salt, recovery_salt, current_key_epoch
       FROM sync_accounts_v2
      WHERE username = ?
      LIMIT 1`
  ).bind(username).first<{
    password_salt: string;
    recovery_salt: string;
    current_key_epoch: number;
  }>();

  if (!account) return c.json({ error: "account_not_found" }, 404);
  return c.json({
    passwordSalt: account.password_salt,
    recoverySalt: account.recovery_salt,
    currentKeyEpoch: Number(account.current_key_epoch)
  });
});

app.post("/v1/sync/auth/register", async (c) => {
  const parsed = syncRegisterSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid_register_payload" }, 400);
  const body = parsed.data;
  const username = body.username.trim().toLowerCase();

  const existing = await c.env.DB.prepare(
    "SELECT id FROM sync_accounts_v2 WHERE username = ? LIMIT 1"
  ).bind(username).first();
  if (existing) return c.json({ error: "username_already_exists" }, 409);

  const userId = crypto.randomUUID();
  const deviceId = crypto.randomUUID();
  const token = randomSyncToken();
  const tokenHash = await sha256Base64(token);

  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO sync_accounts_v2
         (id, username, password_salt, password_verifier, recovery_salt,
          recovery_verifier, current_key_epoch)
       VALUES (?, ?, ?, ?, ?, ?, 1)`
    ).bind(
      userId,
      username,
      body.passwordSalt,
      await sha256Base64(body.passwordVerifier),
      body.recoverySalt,
      await sha256Base64(body.recoveryVerifier)
    ),
    c.env.DB.prepare(
      `INSERT INTO sync_devices_v2
         (id, user_id, device_fingerprint, device_name, token_hash)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(
      deviceId,
      userId,
      body.deviceFingerprint,
      body.deviceName,
      tokenHash
    ),
    c.env.DB.prepare(
      `INSERT INTO sync_key_epochs_v2
         (user_id, epoch, password_wrapped_key, password_nonce,
          recovery_wrapped_key, recovery_nonce)
       VALUES (?, 1, ?, ?, ?, ?)`
    ).bind(
      userId,
      body.keyEnvelope.passwordWrappedKey,
      body.keyEnvelope.passwordNonce,
      body.keyEnvelope.recoveryWrappedKey,
      body.keyEnvelope.recoveryNonce
    )
  ]);

  return c.json({
    ok: true,
    userId,
    deviceId,
    token,
    currentKeyEpoch: 1
  }, 201);
});

app.post("/v1/sync/auth/login", async (c) => {
  const parsed = syncLoginSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid_login_payload" }, 400);
  const body = parsed.data;
  const username = body.username.trim().toLowerCase();

  const account = await c.env.DB.prepare(
    `SELECT id, password_verifier, recovery_verifier, current_key_epoch
       FROM sync_accounts_v2
      WHERE username = ?
      LIMIT 1`
  ).bind(username).first<{
    id: string;
    password_verifier: string;
    recovery_verifier: string;
    current_key_epoch: number;
  }>();

  const passwordVerifierHash = await sha256Base64(body.passwordVerifier);
  if (!account || account.password_verifier !== passwordVerifierHash) {
    return c.json({ error: "invalid_credentials" }, 401);
  }

  const existingDevice = await c.env.DB.prepare(
    `SELECT id, revoked_at
       FROM sync_devices_v2
      WHERE user_id = ? AND device_fingerprint = ?
      LIMIT 1`
  ).bind(account.id, body.deviceFingerprint).first<{
    id: string;
    revoked_at: string | null;
  }>();

  const isNewOrRevoked = !existingDevice || existingDevice.revoked_at != null;
  const recoveryVerifierHash = body.recoveryVerifier
    ? await sha256Base64(body.recoveryVerifier)
    : null;
  if (isNewOrRevoked && recoveryVerifierHash !== account.recovery_verifier) {
    return c.json({ error: "device_enrollment_requires_recovery" }, 403);
  }

  const token = randomSyncToken();
  const tokenHash = await sha256Base64(token);
  let deviceId = existingDevice?.id || crypto.randomUUID();

  if (existingDevice) {
    await c.env.DB.prepare(
      `UPDATE sync_devices_v2
          SET device_name = ?, token_hash = ?, revoked_at = NULL,
              last_seen_at = datetime('now')
        WHERE id = ?`
    ).bind(body.deviceName, tokenHash, deviceId).run();
  } else {
    await c.env.DB.prepare(
      `INSERT INTO sync_devices_v2
         (id, user_id, device_fingerprint, device_name, token_hash)
       VALUES (?, ?, ?, ?, ?)`
    ).bind(deviceId, account.id, body.deviceFingerprint, body.deviceName, tokenHash).run();
  }

  return c.json({
    ok: true,
    userId: account.id,
    deviceId,
    token,
    currentKeyEpoch: Number(account.current_key_epoch),
    keyEnvelopes: await syncKeyEnvelopes(c.env.DB, account.id, "password")
  });
});

app.post("/v1/sync/auth/recovery", async (c) => {
  const body = await c.req.json<{ username?: string; recoveryVerifier?: string }>().catch(() => ({} as { username?: string; recoveryVerifier?: string }));
  const username = (body.username || "").trim().toLowerCase();
  if (!username || !body.recoveryVerifier) {
    return c.json({ error: "missing_recovery_credentials" }, 400);
  }

  const account = await c.env.DB.prepare(
    `SELECT id, recovery_salt, recovery_verifier, current_key_epoch
       FROM sync_accounts_v2
      WHERE username = ?
      LIMIT 1`
  ).bind(username).first<{
    id: string;
    recovery_salt: string;
    recovery_verifier: string;
    current_key_epoch: number;
  }>();

  const recoveryVerifierHash = await sha256Base64(body.recoveryVerifier);
  if (!account || account.recovery_verifier !== recoveryVerifierHash) {
    return c.json({ error: "invalid_recovery_credentials" }, 401);
  }

  return c.json({
    recoverySalt: account.recovery_salt,
    currentKeyEpoch: Number(account.current_key_epoch),
    keyEnvelopes: await syncKeyEnvelopes(c.env.DB, account.id, "recovery")
  });
});

app.post("/v1/sync/auth/reset-password", async (c) => {
  const parsed = syncRecoveryResetPasswordSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid_password_reset_payload" }, 400);
  const body = parsed.data;
  const username = body.username.trim().toLowerCase();

  const account = await c.env.DB.prepare(
    `SELECT id, recovery_verifier
       FROM sync_accounts_v2
      WHERE username = ?
      LIMIT 1`
  ).bind(username).first<{ id: string; recovery_verifier: string }>();

  const recoveryVerifierHash = await sha256Base64(body.recoveryVerifier);
  if (!account || account.recovery_verifier !== recoveryVerifierHash) {
    return c.json({ error: "invalid_recovery_credentials" }, 401);
  }

  const currentEpochs = await c.env.DB.prepare(
    "SELECT epoch FROM sync_key_epochs_v2 WHERE user_id = ? ORDER BY epoch"
  ).bind(account.id).all();
  const expected = currentEpochs.results.map((r) => Number(r.epoch));
  const supplied = body.keyEnvelopes.map((r) => r.epoch).sort((a, b) => a - b);
  if (JSON.stringify(expected) !== JSON.stringify(supplied)) {
    return c.json({ error: "incomplete_key_epoch_set" }, 409);
  }

  const statements = [
    c.env.DB.prepare(
      `UPDATE sync_accounts_v2
          SET password_salt = ?, password_verifier = ?, updated_at = datetime('now')
        WHERE id = ?`
    ).bind(body.passwordSalt, await sha256Base64(body.passwordVerifier), account.id),
    ...body.keyEnvelopes.map((envelope) =>
      c.env.DB.prepare(
        `UPDATE sync_key_epochs_v2
            SET password_wrapped_key = ?, password_nonce = ?
          WHERE user_id = ? AND epoch = ?`
      ).bind(
        envelope.passwordWrappedKey,
        envelope.passwordNonce,
        account.id,
        envelope.epoch
      )
    )
  ];
  await c.env.DB.batch(statements);
  return c.json({ ok: true });
});

app.post("/v1/sync/auth/change-password", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);
  const parsed = syncChangePasswordSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid_password_change_payload" }, 400);
  const body = parsed.data;

  const currentEpochs = await c.env.DB.prepare(
    "SELECT epoch FROM sync_key_epochs_v2 WHERE user_id = ? ORDER BY epoch"
  ).bind(auth.userId).all();
  const expected = currentEpochs.results.map((r) => Number(r.epoch));
  const supplied = body.keyEnvelopes.map((r) => r.epoch).sort((a, b) => a - b);
  if (JSON.stringify(expected) !== JSON.stringify(supplied)) {
    return c.json({ error: "incomplete_key_epoch_set" }, 409);
  }

  await c.env.DB.batch([
    c.env.DB.prepare(
      `UPDATE sync_accounts_v2
          SET password_salt = ?, password_verifier = ?, updated_at = datetime('now')
        WHERE id = ?`
    ).bind(body.passwordSalt, await sha256Base64(body.passwordVerifier), auth.userId),
    ...body.keyEnvelopes.map((envelope) =>
      c.env.DB.prepare(
        `UPDATE sync_key_epochs_v2
            SET password_wrapped_key = ?, password_nonce = ?
          WHERE user_id = ? AND epoch = ?`
      ).bind(
        envelope.passwordWrappedKey,
        envelope.passwordNonce,
        auth.userId,
        envelope.epoch
      )
    )
  ]);
  return c.json({ ok: true });
});

app.get("/v1/sync/keys", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);

  const account = await c.env.DB.prepare(
    "SELECT password_salt, current_key_epoch FROM sync_accounts_v2 WHERE id = ?"
  ).bind(auth.userId).first<{ password_salt: string; current_key_epoch: number }>();

  return c.json({
    passwordSalt: account?.password_salt,
    currentKeyEpoch: Number(account?.current_key_epoch ?? 1),
    keyEnvelopes: await syncKeyEnvelopes(c.env.DB, auth.userId, "password")
  });
});

app.post("/v1/sync/keys/rotate", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);
  const parsed = syncRotateKeySchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid_key_rotation_payload" }, 400);
  const body = parsed.data;

  const account = await c.env.DB.prepare(
    "SELECT current_key_epoch FROM sync_accounts_v2 WHERE id = ?"
  ).bind(auth.userId).first<{ current_key_epoch: number }>();
  const currentEpoch = Number(account?.current_key_epoch ?? 0);
  if (body.newEpoch !== currentEpoch + 1) {
    return c.json({ error: "invalid_next_key_epoch", currentKeyEpoch: currentEpoch }, 409);
  }

  await c.env.DB.batch([
    c.env.DB.prepare(
      `INSERT INTO sync_key_epochs_v2
         (user_id, epoch, password_wrapped_key, password_nonce,
          recovery_wrapped_key, recovery_nonce)
       VALUES (?, ?, ?, ?, ?, ?)`
    ).bind(
      auth.userId,
      body.newEpoch,
      body.passwordWrappedKey,
      body.passwordNonce,
      body.recoveryWrappedKey,
      body.recoveryNonce
    ),
    c.env.DB.prepare(
      `UPDATE sync_accounts_v2
          SET current_key_epoch = ?, updated_at = datetime('now')
        WHERE id = ?`
    ).bind(body.newEpoch, auth.userId)
  ]);

  return c.json({ ok: true, currentKeyEpoch: body.newEpoch });
});

app.post("/v1/sync/push", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);
  const parsed = syncPushSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid_sync_payload" }, 400);
  const body = parsed.data;

  const account = await c.env.DB.prepare(
    "SELECT current_key_epoch FROM sync_accounts_v2 WHERE id = ?"
  ).bind(auth.userId).first<{ current_key_epoch: number }>();
  const currentKeyEpoch = Number(account?.current_key_epoch ?? 1);

  if (body.records.some((r) => r.keyEpoch !== currentKeyEpoch)) {
    return c.json({ error: "stale_key_epoch", currentKeyEpoch }, 409);
  }

  const statements: D1PreparedStatement[] = [];
  for (const r of body.records) {
    statements.push(
      c.env.DB.prepare(
        `INSERT INTO sync_records_v2
           (user_id, entity_type, entity_id, ciphertext, nonce, aad,
            envelope_version, key_epoch, revision, deleted, client_updated_at,
            source_device_id, server_received_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now'))
         ON CONFLICT(user_id, entity_type, entity_id)
         DO UPDATE SET
           ciphertext = excluded.ciphertext,
           nonce = excluded.nonce,
           aad = excluded.aad,
           envelope_version = excluded.envelope_version,
           key_epoch = excluded.key_epoch,
           revision = excluded.revision,
           deleted = excluded.deleted,
           client_updated_at = excluded.client_updated_at,
           source_device_id = excluded.source_device_id,
           server_received_at = datetime('now')
         WHERE excluded.revision > sync_records_v2.revision
            OR (excluded.revision = sync_records_v2.revision
                AND excluded.client_updated_at > sync_records_v2.client_updated_at)
            OR (excluded.revision = sync_records_v2.revision
                AND excluded.client_updated_at = sync_records_v2.client_updated_at
                AND excluded.source_device_id > sync_records_v2.source_device_id)`
      ).bind(
        auth.userId,
        r.entityType,
        r.entityId,
        r.ciphertext,
        r.nonce,
        r.aad,
        r.envelopeVersion,
        r.keyEpoch,
        r.revision,
        r.deleted ? 1 : 0,
        r.clientUpdatedAt,
        auth.deviceId
      ),
      c.env.DB.prepare(
        `INSERT INTO sync_changes_v2
           (user_id, entity_type, entity_id)
         VALUES (?, ?, ?)`
      ).bind(auth.userId, r.entityType, r.entityId)
    );
  }

  if (statements.length) await c.env.DB.batch(statements);
  return c.json({
    ok: true,
    acceptedCount: body.records.length,
    currentKeyEpoch
  });
});

app.get("/v1/sync/pull", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);

  const cursor = Math.max(0, Number.parseInt(c.req.query("cursor") || "0", 10) || 0);
  const limit = Math.min(500, Math.max(1, Number.parseInt(c.req.query("limit") || "200", 10) || 200));

  const rows = await c.env.DB.prepare(
    `SELECT ch.change_id,
            r.entity_type, r.entity_id, r.ciphertext, r.nonce, r.aad,
            r.envelope_version, r.key_epoch, r.revision, r.deleted,
            r.client_updated_at, r.source_device_id, r.server_received_at
       FROM sync_changes_v2 ch
       JOIN sync_records_v2 r
         ON r.user_id = ch.user_id
        AND r.entity_type = ch.entity_type
        AND r.entity_id = ch.entity_id
      WHERE ch.user_id = ? AND ch.change_id > ?
      ORDER BY ch.change_id ASC
      LIMIT ?`
  ).bind(auth.userId, cursor, limit).all();

  let nextCursor = cursor;
  const records = rows.results.map((r) => {
    nextCursor = Math.max(nextCursor, Number(r.change_id));
    return {
      entityType: String(r.entity_type),
      entityId: String(r.entity_id),
      ciphertext: String(r.ciphertext),
      nonce: String(r.nonce),
      aad: String(r.aad),
      envelopeVersion: Number(r.envelope_version),
      keyEpoch: Number(r.key_epoch),
      revision: Number(r.revision),
      deleted: Boolean(r.deleted),
      clientUpdatedAt: String(r.client_updated_at),
      sourceDeviceId: String(r.source_device_id),
      serverReceivedAt: String(r.server_received_at)
    };
  });

  const account = await c.env.DB.prepare(
    "SELECT current_key_epoch FROM sync_accounts_v2 WHERE id = ?"
  ).bind(auth.userId).first<{ current_key_epoch: number }>();

  return c.json({
    ok: true,
    records,
    nextCursor,
    hasMore: records.length === limit,
    currentKeyEpoch: Number(account?.current_key_epoch ?? 1)
  });
});

app.get("/v1/sync/devices", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);

  const rows = await c.env.DB.prepare(
    `SELECT id, device_fingerprint, device_name, created_at, last_seen_at, revoked_at
       FROM sync_devices_v2
      WHERE user_id = ?
      ORDER BY revoked_at IS NULL DESC, last_seen_at DESC`
  ).bind(auth.userId).all();

  return c.json({
    devices: rows.results.map((r) => ({
      id: String(r.id),
      deviceFingerprint: String(r.device_fingerprint),
      deviceName: String(r.device_name),
      createdAt: String(r.created_at),
      lastSeenAt: String(r.last_seen_at),
      revokedAt: r.revoked_at == null ? null : String(r.revoked_at),
      current: String(r.id) === auth.deviceId
    }))
  });
});

app.post("/v1/sync/devices/:deviceId/revoke", async (c) => {
  const auth = await authenticateSync(c);
  if (!auth) return c.json({ error: "unauthorized" }, 401);
  const deviceId = c.req.param("deviceId");
  if (deviceId === auth.deviceId) {
    return c.json({ error: "cannot_revoke_current_device" }, 409);
  }

  const result = await c.env.DB.prepare(
    `UPDATE sync_devices_v2
        SET revoked_at = datetime('now'), token_hash = ''
      WHERE id = ? AND user_id = ? AND revoked_at IS NULL`
  ).bind(deviceId, auth.userId).run();

  if (!result.meta.changes) return c.json({ error: "device_not_found" }, 404);

  const account = await c.env.DB.prepare(
    "SELECT current_key_epoch FROM sync_accounts_v2 WHERE id = ?"
  ).bind(auth.userId).first<{ current_key_epoch: number }>();

  return c.json({
    ok: true,
    rotationRequired: true,
    currentKeyEpoch: Number(account?.current_key_epoch ?? 1)
  });
});

export default app;

