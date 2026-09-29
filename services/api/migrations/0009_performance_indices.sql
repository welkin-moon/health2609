CREATE INDEX IF NOT EXISTS idx_meal_consumption_dish ON meal_consumption(dish_id);
CREATE INDEX IF NOT EXISTS idx_pe_sessions_date ON pe_sessions(date);
CREATE INDEX IF NOT EXISTS idx_manual_activity_date ON manual_activity_sessions(date);
CREATE INDEX IF NOT EXISTS idx_outside_school_activity_date ON outside_school_activity_daily(date);
CREATE INDEX IF NOT EXISTS idx_pe_timetable_weekday ON pe_timetable(school_id, weekday);
