import { Hono } from "hono";
import { cors } from "hono/cors";
import { zValidator } from "@hono/zod-validator";
import {
  adminUpsertMenuSchema,
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

app.use("/v1/*", cors({
  origin: "*",
  allowHeaders: ["Content-Type", "Authorization", "x-demo-school", "x-demo-participant", "x-demo-role"],
  allowMethods: ["GET", "POST", "PUT", "DELETE", "OPTIONS"]
}));

app.use("/v1/*", async (c, next) => {
  // Deliberately small demo auth shell. Production auth replaces these headers.
  const schoolId = c.req.header("x-demo-school") ?? "demo-school";
  const participantId = c.req.header("x-demo-participant") ?? "demo-student";
  const role = c.req.header("x-demo-role") === "admin" ? "admin" : "student";
  c.set("schoolId", schoolId);
  c.set("participantId", participantId);
  c.set("role", role);
  await next();
});

const requireAdmin = (role: string) => role === "admin";

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

  const menus = new Map<string, { id: string; mealSlot: string; dishes: unknown[] }>();
  for (const row of rows.results) {
    const menuId = String(row.menu_id);
    const menu = menus.get(menuId) ?? { id: menuId, mealSlot: String(row.meal_slot), dishes: [] };
    if (row.id) {
      menu.dishes.push({
        id: String(row.id),
        name: String(row.name),
        standardServingGrams: row.standard_serving_grams == null ? null : Number(row.standard_serving_grams),
        nutritionPerServing: row.nutrition_per_serving_json
          ? JSON.parse(String(row.nutrition_per_serving_json))
          : null
      });
    }
    menus.set(menuId, menu);
  }

  return c.json({ date, menus: [...menus.values()] });
});

app.put("/v1/admin/menus", zValidator("json", adminUpsertMenuSchema), async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);
  const body = c.req.valid("json");
  const schoolId = c.get("schoolId");

  let menu = await c.env.DB.prepare(
    "SELECT id FROM menus WHERE school_id = ? AND date = ? AND meal_slot = ? LIMIT 1"
  ).bind(schoolId, body.date, body.mealSlot).first<{ id: string }>();

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
           (id, menu_id, name, standard_serving_grams, nutrition_per_serving_json, sort_order)
         VALUES (?, ?, ?, ?, ?, ?)`
      ).bind(
        crypto.randomUUID(),
        menu!.id,
        dish.name,
        dish.standardServingGrams,
        dish.nutritionPerServing ? JSON.stringify(dish.nutritionPerServing) : null,
        index
      )
    )
  ];

  await c.env.DB.batch(statements);
  return c.json({ ok: true, menuId: menu.id });
});

app.get("/v1/admin/stats/overview", async (c) => {
  if (!requireAdmin(c.get("role"))) return c.json({ error: "forbidden" }, 403);
  const date = c.req.query("date");
  if (!date) return c.json({ error: "date_required" }, 400);

  const schoolId = c.get("schoolId");

  const [membershipCount, meal, pe, activity] = await Promise.all([
    c.env.DB.prepare(
      "SELECT COUNT(*) AS count FROM student_memberships WHERE school_id = ?"
    ).bind(schoolId).first<{ count: number }>(),
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

  const total = Number(membershipCount?.count ?? 0);
  const participants = Number((meal as { participants?: number } | null)?.participants ?? 0);

  return c.json({
    date,
    meal: {
      ...(meal ?? {}),
      participationRate: total === 0 ? 0 : participants / total
    },
    pe,
    activity
  });
});

export default app;
