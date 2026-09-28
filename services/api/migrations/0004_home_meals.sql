CREATE TABLE home_meals (
  id TEXT PRIMARY KEY,
  student_membership_id TEXT NOT NULL REFERENCES student_memberships(id) ON DELETE CASCADE,
  date TEXT NOT NULL,
  meal_slot TEXT NOT NULL CHECK(meal_slot IN ('breakfast','lunch','dinner')),
  confirmed_items_json TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_home_meals_student_date
  ON home_meals(student_membership_id, date);
