DELETE FROM home_meals
WHERE rowid NOT IN (
  SELECT MAX(rowid)
  FROM home_meals
  GROUP BY student_membership_id, date, meal_slot
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_home_meals_student_date_slot
  ON home_meals(student_membership_id, date, meal_slot);
