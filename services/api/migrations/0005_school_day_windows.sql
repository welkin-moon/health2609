CREATE TABLE school_day_windows (
  id TEXT PRIMARY KEY,
  school_id TEXT NOT NULL REFERENCES schools(id) ON DELETE CASCADE,
  weekday INTEGER NOT NULL CHECK(weekday BETWEEN 1 AND 7),
  start_time TEXT NOT NULL,
  end_time TEXT NOT NULL,
  UNIQUE(school_id, weekday, start_time, end_time)
);

CREATE INDEX idx_school_day_windows_school_weekday
  ON school_day_windows(school_id, weekday);
