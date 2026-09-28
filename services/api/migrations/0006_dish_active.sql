ALTER TABLE dishes
  ADD COLUMN active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1));

CREATE INDEX idx_dishes_menu_active
  ON dishes(menu_id, active);
