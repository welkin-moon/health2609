-- 0013_sync_v2.sql
-- Replaces the incomplete 0012 sync schema with a versioned, authenticated,
-- cursor-based E2EE sync store. 0012 is intentionally left in place so
-- already-deployed databases can migrate forward safely.

CREATE TABLE IF NOT EXISTS sync_accounts_v2 (
  id TEXT PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  password_salt TEXT NOT NULL,
  password_verifier TEXT NOT NULL,
  recovery_salt TEXT NOT NULL,
  recovery_verifier TEXT NOT NULL,
  current_key_epoch INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS sync_devices_v2 (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  device_fingerprint TEXT NOT NULL,
  device_name TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  last_seen_at TEXT NOT NULL DEFAULT (datetime('now')),
  revoked_at TEXT,
  FOREIGN KEY (user_id) REFERENCES sync_accounts_v2(id) ON DELETE CASCADE,
  UNIQUE(user_id, device_fingerprint)
);

CREATE INDEX IF NOT EXISTS idx_sync_devices_v2_user
  ON sync_devices_v2(user_id, revoked_at, last_seen_at);

CREATE TABLE IF NOT EXISTS sync_key_epochs_v2 (
  user_id TEXT NOT NULL,
  epoch INTEGER NOT NULL,
  password_wrapped_key TEXT NOT NULL,
  password_nonce TEXT NOT NULL,
  recovery_wrapped_key TEXT NOT NULL,
  recovery_nonce TEXT NOT NULL,
  created_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, epoch),
  FOREIGN KEY (user_id) REFERENCES sync_accounts_v2(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS sync_records_v2 (
  user_id TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  ciphertext TEXT NOT NULL,
  nonce TEXT NOT NULL,
  aad TEXT NOT NULL,
  envelope_version INTEGER NOT NULL DEFAULT 1,
  key_epoch INTEGER NOT NULL DEFAULT 1,
  revision INTEGER NOT NULL DEFAULT 1,
  deleted INTEGER NOT NULL DEFAULT 0,
  client_updated_at TEXT NOT NULL,
  source_device_id TEXT NOT NULL,
  server_received_at TEXT NOT NULL DEFAULT (datetime('now')),
  PRIMARY KEY (user_id, entity_type, entity_id),
  FOREIGN KEY (user_id) REFERENCES sync_accounts_v2(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_records_v2_user_updated
  ON sync_records_v2(user_id, server_received_at);

CREATE TABLE IF NOT EXISTS sync_changes_v2 (
  change_id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  server_received_at TEXT NOT NULL DEFAULT (datetime('now')),
  FOREIGN KEY (user_id) REFERENCES sync_accounts_v2(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sync_changes_v2_user_cursor
  ON sync_changes_v2(user_id, change_id);
