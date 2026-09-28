import { Hono } from "hono";
import { zValidator } from "@hono/zod-validator";
import {
  outsideActivitySchema,
  recordMealSchema,
  todayMenuSchema
} from "@health2609/contracts";

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

const app = new Hono<{ Bindings: Bindings; Variables: Variables }>();

app.use("/v1/*", async (c, next) => {
  // Demo auth shell. Replace with signed sessions before exposing publicly.
  const schoolId = c.req.header("x-demo-school") ?? "demo-school";
  const participantId = c.req.header("x-demo-participant") ?? "demo-student";
  const role = c.req.header("x-demo-role") === "admin" ? "admin" : "student";
  c.set("schoolId", schoolId);
  c.set("participantId", participantId);
  c.set("role", role);
  await next();
});

app.get("/health", (c) => c.json({ ok: true }));

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
      standardServingGrams: row.standard_serving_grams == null ? null : Number(row.standard_serving_grams),
      nutritionPerServing: row.nutrition_per_serving_json
        ? JSON.parse(String(row.nutrition_per_serving_json))
        : null
    }))
  });

  return c.json(payload);
});

app.post("/v1/meals/consumption", zValidator("json", recordMealSchema), async (c) => {
  const body = c.req.valid("json");
  const participantId = c.get("participantId");
  const schoolId = c.get("schoolId");

  const membership = await c.env.DB.prepare(
    "SELECT id FROM student_memberships WHERE school_id = ? AND participant_id = ? LIMIT 1"
  ).bind(schoolId, participantId).first<{ id: string }>();

  if (!membership) return c.json({ error: "membership_not_found" }, 404);

  const statements = body.items.map((item) =>
    c.env.DB.prepare(
      `INSERT INTO meal_consumption
         (id, student_membership_id, dish_id, serving_multiplier, consumed_at)
       VALUES (?, ?, ?, ?, datetime('now'))
       ON CONFLICT(student_membership_id, dish_id)
       DO UPDATE SET serving_multiplier = excluded.serving_multiplier,
                     consumed_at = excluded.consumed_at`
    ).bind(crypto.randomUUID(), membership.id, item.dishId, item.servingMultiplier)
  );

  if (statements.length) await c.env.DB.batch(statements);
  return c.json({ ok: true });
});

app.post("/v1/activity/outside-school", zValidator("json", outsideActivitySchema), async (c) => {
  const body = c.req.valid("json");
  const membership = await c.env.DB.prepare(
    "SELECT id FROM student_memberships WHERE school_id = ? AND participant_id = ? LIMIT 1"
  ).bind(c.get("schoolId"), c.get("participantId")).first<{ id: string }>();

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
});

app.get("/v1/admin/stats/overview", async (c) => {
  if (c.get("role") !== "admin") return c.json({ error: "forbidden" }, 403);
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const schoolId = c.get("schoolId");

  const [meal, pe, activity] = await Promise.all([
    c.env.DB.prepare(
      `SELECT COUNT(DISTINCT sm.id) AS participants,
              AVG(mc.serving_multiplier) AS avg_serving_multiplier
         FROM meal_consumption mc
         JOIN student_memberships sm ON sm.id = mc.student_membership_id
         JOIN dishes d ON d.id = mc.dish_id
         JOIN menus m ON m.id = d.menu_id
        WHERE sm.school_id = ? AND m.date = ?`
    ).bind(schoolId, date).first(),
    c.env.DB.prepare(
      `SELECT AVG(ps.actual_activity_minutes) AS avg_pe_minutes,
              COUNT(*) AS recorded_sessions
         FROM pe_sessions ps
         JOIN pe_timetable pt ON pt.id = ps.timetable_id
        WHERE pt.school_id = ? AND ps.date = ?`
    ).bind(schoolId, date).first(),
    c.env.DB.prepare(
      `SELECT AVG(a.exercise_minutes) AS avg_outside_minutes
         FROM outside_school_activity_daily a
         JOIN student_memberships sm ON sm.id = a.student_membership_id
        WHERE sm.school_id = ? AND a.date = ?`
    ).bind(schoolId, date).first()
  ]);

  return c.json({ date, meal, pe, activity });
});

export default app;
