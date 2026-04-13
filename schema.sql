CREATE TABLE IF NOT EXISTS projects (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    description   TEXT,
    display_order INTEGER DEFAULT 0,
    created_at    TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS tickets (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    title         TEXT NOT NULL,
    description   TEXT,
    status        TEXT NOT NULL DEFAULT 'TODO',
    project_id    INTEGER NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    display_order INTEGER DEFAULT 0,
    created_at    TEXT DEFAULT (datetime('now'))
);
