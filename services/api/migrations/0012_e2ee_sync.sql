-- 0012_e2ee_sync.sql: Cloudflare D1 end-to-end encrypted multi-device sync
CREATE TABLE IF NOT EXISTS sync_users (
  id TEXT PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_devices (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  device_name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  last_sync_at INTEGER NOT NULL,
  FOREIGN KEY (user_id) REFERENCES sync_users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sync_records (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  record_type TEXT NOT NULL, -- 'meal', 'activity', 'preference'
  record_date TEXT NOT NULL,
  ciphertext TEXT NOT NULL, -- AES-GCM base64 encrypted payload
  iv TEXT NOT NULL, -- AES-GCM base64 IV
  thumbnail_data TEXT, -- optional low-bit-depth downscaled encrypted thumbnail base64
  version INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  FOREIGN KEY (user_id) REFERENCES sync_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_records_user_updated ON sync_records(user_id, updated_at);
