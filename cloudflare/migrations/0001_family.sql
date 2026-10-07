PRAGMA foreign_keys = ON;
CREATE TABLE families (id TEXT PRIMARY KEY, created_at TEXT NOT NULL);
CREATE TABLE devices (id TEXT PRIMARY KEY, family_id TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE, token_hash TEXT NOT NULL UNIQUE, label TEXT NOT NULL, created_at TEXT NOT NULL);
CREATE INDEX devices_family ON devices(family_id);
CREATE TABLE invites (code_hash TEXT PRIMARY KEY, family_id TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE, expires_at TEXT NOT NULL);
CREATE TABLE reservations (family_id TEXT NOT NULL REFERENCES families(id) ON DELETE CASCADE, device_id TEXT NOT NULL, entry_id TEXT NOT NULL, vehicle TEXT NOT NULL, start_date TEXT NOT NULL, end_date TEXT NOT NULL, attempted_at TEXT NOT NULL, status TEXT NOT NULL, succeeded INTEGER NOT NULL, total INTEGER NOT NULL, author TEXT NOT NULL, PRIMARY KEY(family_id,device_id,entry_id));
CREATE INDEX reservations_recent ON reservations(family_id,attempted_at DESC);
CREATE TABLE rate_limits (bucket TEXT PRIMARY KEY, count INTEGER NOT NULL, minute INTEGER NOT NULL);
