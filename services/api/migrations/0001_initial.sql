PRAGMA foreign_keys = ON;

CREATE TABLE schools (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  timezone TEXT NOT NULL DEFAULT 'Asia/Shanghai',
  daily_activity_target_minutes INTEGER NOT NULL DEFAULT 120,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE class_groups (
  id TEXT PRIMARY KEY,
  school_id TEXT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
  name TEXT NOT NULL
);

CREATE TABLE student_memberships (
  id TEXT PRIMARY KEY,
  school_id TEXT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
  class_group_id TEXT REFERENCES class_groups(id) ON DELETE SET NULL,
  participant_id TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(school_id, participant_id)
);

CREATE TABLE menus (
  id TEXT PRIMARY KEY,
  school_id TEXT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  meal_slot TEXT NOT NULL CHECK(meal_slot IN ('breakfast','lunch','dinner')),
  UNIQUE(school_id, date, meal_slot)
);

CREATE TABLE dishes (
  id TEXT PRIMARY KEY,
  menu_id TEXT NOT NULL REFERENCES menus(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  standard_serving_grams REAL,
  nutrition_per_serving_json TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE meal_consumption (
  id TEXT PRIMARY KEY,
  student_membership_id TEXT NOT NULL REFERENCES student_memberships(id) ON DELETE CASCADE,
  dish_id TEXT NOT NULL REFERENCES dishes(id) ON DELETE CASCADE,
  serving_multiplier REAL NOT NULL CHECK(serving_multiplier >= 0 AND serving_multiplier <= 5),
  consumed_at TEXT NOT NULL,
  UNIQUE(student_membership_id, dish_id)
);

CREATE TABLE pe_timetable (
  id TEXT PRIMARY KEY,
  school_id TEXT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
  class_group_id TEXT NOT NULL REFERENCES class_groups(id) ON DELETE CASCADE,
  weekday INTEGER NOT NULL CHECK(weekday BETWEEN 1 AND 7),
  start_time TEXT NOT NULL,
  end_time TEXT NOT NULL
);

CREATE TABLE pe_sessions (
  id TEXT PRIMARY KEY,
  timetable_id TEXT NOT NULL REFERENCES pe_timetable(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  actual_activity_minutes INTEGER NOT NULL CHECK(actual_activity_minutes BETWEEN 0 AND 300),
  recorded_by_admin_id TEXT,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  UNIQUE(timetable_id, date)
);

CREATE TABLE outside_school_activity_daily (
  id TEXT PRIMARY KEY,
  student_membership_id TEXT NOT NULL REFERENCES student_memberships(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  exercise_minutes INTEGER NOT NULL CHECK(exercise_minutes BETWEEN 0 AND 1440),
  steps INTEGER,
  active_energy_kcal REAL,
  UNIQUE(student_membership_id, date)
);

CREATE INDEX idx_menus_school_date ON menus(school_id, date);
CREATE INDEX idx_consumption_student ON meal_consumption(student_membership_id);
CREATE INDEX idx_activity_student_date ON outside_school_activity_daily(student_membership_id, date);
