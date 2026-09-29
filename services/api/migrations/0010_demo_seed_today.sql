-- Migration 0010_demo_seed_today.sql
-- Seeds menus, dishes, PE timetables, and PE sessions for current demo dates
-- (e.g. 2026-09-29 and dynamic rolling dates) to prevent empty app states.

-- 1. Ensure student memberships exist for demo class (cohort >= 3)
INSERT OR IGNORE INTO student_memberships (
  id,
  school_id,
  class_group_id,
  participant_id
) VALUES
  ('demo-membership-2', 'demo-school', 'demo-class', 'demo-student-2'),
  ('demo-membership-3', 'demo-school', 'demo-class', 'demo-student-3'),
  ('demo-membership-4', 'demo-school', 'demo-class', 'demo-student-4');

-- 2. PE timetable for demo class across weekdays (1=Mon ... 5=Fri)
INSERT OR IGNORE INTO pe_timetable (
  id,
  school_id,
  class_group_id,
  weekday,
  start_time,
  end_time
) VALUES
  ('demo-pe-timetable-mon', 'demo-school', 'demo-class', 1, '09:00', '10:00'),
  ('demo-pe-timetable-tue', 'demo-school', 'demo-class', 2, '10:00', '11:00'),
  ('demo-pe-timetable-wed', 'demo-school', 'demo-class', 3, '14:00', '15:00'),
  ('demo-pe-timetable-thu', 'demo-school', 'demo-class', 4, '10:00', '11:00'),
  ('demo-pe-timetable-fri', 'demo-school', 'demo-class', 5, '15:00', '16:00');

-- 3. Menus for competition demo date: 2026-09-29 (Tuesday)
INSERT OR IGNORE INTO menus (
  id,
  school_id,
  date,
  meal_slot
) VALUES
  ('demo-menu-20260929-breakfast', 'demo-school', '2026-09-29', 'breakfast'),
  ('demo-menu-20260929-lunch',     'demo-school', '2026-09-29', 'lunch'),
  ('demo-menu-20260929-dinner',    'demo-school', '2026-09-29', 'dinner');

-- 4. Dishes for 2026-09-29 lunch
INSERT OR IGNORE INTO dishes (
  id,
  menu_id,
  name,
  standard_serving_grams,
  nutrition_per_serving_json,
  sort_order,
  active
) VALUES
  (
    'demo-dish-20260929-kungpao',
    'demo-menu-20260929-lunch',
    '宫保鸡丁',
    150,
    '{"energyKcal":280,"proteinG":24,"fatG":14,"carbohydrateG":12,"fiberG":2,"sodiumMg":450,"sugarG":5,"saturatedFatG":3}',
    1,
    1
  ),
  (
    'demo-dish-20260929-greens',
    'demo-menu-20260929-lunch',
    '清炒时蔬',
    120,
    '{"energyKcal":75,"proteinG":3,"fatG":4,"carbohydrateG":8,"fiberG":4,"sodiumMg":180,"sugarG":2,"saturatedFatG":0.5}',
    2,
    1
  ),
  (
    'demo-dish-20260929-soup',
    'demo-menu-20260929-lunch',
    '紫菜蛋花汤',
    200,
    '{"energyKcal":60,"proteinG":5,"fatG":3,"carbohydrateG":4,"fiberG":1,"sodiumMg":320,"sugarG":1,"saturatedFatG":0.5}',
    3,
    1
  ),
  (
    'demo-dish-20260929-rice',
    'demo-menu-20260929-lunch',
    '五谷米饭',
    180,
    '{"energyKcal":230,"proteinG":5,"fatG":1,"carbohydrateG":48,"fiberG":3,"sodiumMg":5,"sugarG":0,"saturatedFatG":0.2}',
    4,
    1
  );

-- 5. PE session for 2026-09-29 (Tuesday)
INSERT OR IGNORE INTO pe_sessions (
  id,
  timetable_id,
  date,
  actual_activity_minutes,
  recorded_by_admin_id
) VALUES (
  'demo-pe-session-20260929',
  'demo-pe-timetable-tue',
  '2026-09-29',
  45,
  'demo-admin'
);

-- 6. Meal consumption records for 2026-09-29 (ensures cohort >= 3 for demo stats)
INSERT OR IGNORE INTO meal_consumption (
  id,
  student_membership_id,
  dish_id,
  serving_multiplier,
  consumed_grams,
  consumed_at
) VALUES
  ('demo-mc-20260929-1', 'demo-membership',   'demo-dish-20260929-kungpao', 1.0, 150, '2026-09-29T12:05:00Z'),
  ('demo-mc-20260929-2', 'demo-membership',   'demo-dish-20260929-greens',  1.0, 120, '2026-09-29T12:05:00Z'),
  ('demo-mc-20260929-3', 'demo-membership',   'demo-dish-20260929-rice',    1.0, 180, '2026-09-29T12:05:00Z'),
  ('demo-mc-20260929-4', 'demo-membership-2', 'demo-dish-20260929-kungpao', 1.0, 150, '2026-09-29T12:10:00Z'),
  ('demo-mc-20260929-5', 'demo-membership-2', 'demo-dish-20260929-greens',  1.0, 120, '2026-09-29T12:10:00Z'),
  ('demo-mc-20260929-6', 'demo-membership-3', 'demo-dish-20260929-kungpao', 0.8, 120, '2026-09-29T12:15:00Z'),
  ('demo-mc-20260929-7', 'demo-membership-3', 'demo-dish-20260929-soup',    1.0, 200, '2026-09-29T12:15:00Z');

-- 7. Outside-school activity records for 2026-09-29
INSERT OR IGNORE INTO outside_school_activity_daily (
  id,
  student_membership_id,
  date,
  exercise_minutes,
  steps,
  active_energy_kcal
) VALUES
  ('demo-act-20260929-1', 'demo-membership',   '2026-09-29', 50, 7500, 320),
  ('demo-act-20260929-2', 'demo-membership-2', '2026-09-29', 40, 6200, 260),
  ('demo-act-20260929-3', 'demo-membership-3', '2026-09-29', 45, 6800, 290);

-- 8. Dynamic rolling today: seed menus and dishes for date('now') if date != '2026-09-29'
INSERT OR IGNORE INTO menus (
  id,
  school_id,
  date,
  meal_slot
) VALUES
  ('demo-menu-today-lunch',  'demo-school', date('now'), 'lunch'),
  ('demo-menu-today-dinner', 'demo-school', date('now'), 'dinner');

INSERT OR IGNORE INTO dishes (
  id,
  menu_id,
  name,
  standard_serving_grams,
  nutrition_per_serving_json,
  sort_order,
  active
)
SELECT
  'demo-dish-today-kungpao',
  'demo-menu-today-lunch',
  '宫保鸡丁',
  150,
  '{"energyKcal":280,"proteinG":24,"fatG":14,"carbohydrateG":12,"fiberG":2,"sodiumMg":450,"sugarG":5,"saturatedFatG":3}',
  1,
  1
WHERE EXISTS (SELECT 1 FROM menus WHERE id = 'demo-menu-today-lunch');

INSERT OR IGNORE INTO dishes (
  id,
  menu_id,
  name,
  standard_serving_grams,
  nutrition_per_serving_json,
  sort_order,
  active
)
SELECT
  'demo-dish-today-greens',
  'demo-menu-today-lunch',
  '清炒时蔬',
  120,
  '{"energyKcal":75,"proteinG":3,"fatG":4,"carbohydrateG":8,"fiberG":4,"sodiumMg":180,"sugarG":2,"saturatedFatG":0.5}',
  2,
  1
WHERE EXISTS (SELECT 1 FROM menus WHERE id = 'demo-menu-today-lunch');

INSERT OR IGNORE INTO dishes (
  id,
  menu_id,
  name,
  standard_serving_grams,
  nutrition_per_serving_json,
  sort_order,
  active
)
SELECT
  'demo-dish-today-rice',
  'demo-menu-today-lunch',
  '五谷米饭',
  180,
  '{"energyKcal":230,"proteinG":5,"fatG":1,"carbohydrateG":48,"fiberG":3,"sodiumMg":5,"sugarG":0,"saturatedFatG":0.2}',
  3,
  1
WHERE EXISTS (SELECT 1 FROM menus WHERE id = 'demo-menu-today-lunch');

-- 9. Dynamic rolling today: PE session matching today's weekday
INSERT OR IGNORE INTO pe_sessions (
  id,
  timetable_id,
  date,
  actual_activity_minutes,
  recorded_by_admin_id
)
SELECT
  'demo-pe-session-today',
  pt.id,
  date('now'),
  45,
  'demo-admin'
FROM pe_timetable pt
WHERE pt.school_id = 'demo-school'
  AND pt.class_group_id = 'demo-class'
  AND pt.weekday = ((CAST(strftime('%w', 'now') AS INTEGER) + 6) % 7 + 1)
LIMIT 1;
