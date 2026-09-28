ALTER TABLE meal_consumption
  ADD COLUMN consumed_grams REAL;

CREATE TABLE manual_activity_sessions (
  id TEXT PRIMARY KEY,
  student_membership_id TEXT NOT NULL REFERENCES student_memberships(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  activity_type TEXT NOT NULL,
  start_time TEXT,
  duration_minutes INTEGER NOT NULL CHECK(duration_minutes BETWEEN 1 AND 600),
  intensity TEXT NOT NULL CHECK(intensity IN ('light','moderate','vigorous')),
  estimated_active_energy_kcal REAL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE student_preferences (
  student_membership_id TEXT PRIMARY KEY REFERENCES student_memberships(id) ON DELETE CASCADE,
  daily_energy_reference_kcal INTEGER,
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_manual_activity_student_date
  ON manual_activity_sessions(student_membership_id, date);

CREATE UNIQUE INDEX idx_pe_timetable_unique_slot
  ON pe_timetable(school_id, class_group_id, weekday, start_time, end_time);
