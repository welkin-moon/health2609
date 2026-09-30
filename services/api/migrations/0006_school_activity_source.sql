CREATE TABLE student_school_activity_overrides (
  student_membership_id TEXT NOT NULL
    REFERENCES student_memberships(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  source TEXT NOT NULL CHECK(source IN ('school','health_connect')),
  exercise_minutes INTEGER CHECK(exercise_minutes BETWEEN 0 AND 1440),
  steps INTEGER,
  active_energy_kcal REAL,
  updated_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY(student_membership_id, date)
);

CREATE INDEX idx_school_activity_override_student_date
  ON student_school_activity_overrides(student_membership_id, date);
