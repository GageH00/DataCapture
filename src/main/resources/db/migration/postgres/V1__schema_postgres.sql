-- Comic Data Capture — PostgreSQL schema (enterprise deployment)
-- Used when db.properties contains a jdbc:postgresql:// URL.
-- Applied by Flyway instead of V1__schema_h2.sql.

CREATE TABLE IF NOT EXISTS sessions (
    id         SERIAL PRIMARY KEY,
    started_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS batches (
    id         SERIAL PRIMARY KEY,
    session_id INT REFERENCES sessions(id),
    item_label TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS field_definitions (
    id          SERIAL PRIMARY KEY,
    session_id  INT REFERENCES sessions(id),
    field_order INT NOT NULL,
    label       TEXT NOT NULL,
    db_key      TEXT NOT NULL,
    field_type  TEXT NOT NULL,
    required    BOOLEAN DEFAULT FALSE,
    options     TEXT
);

CREATE TABLE IF NOT EXISTS entry (
    id           SERIAL PRIMARY KEY,
    batch_id     INT REFERENCES batches(id),
    captured_at  TIMESTAMP DEFAULT NOW(),
    data         JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS images (
    id          SERIAL PRIMARY KEY,
    batch_id    INT REFERENCES batches(id),
    entry_id    INT REFERENCES entry(id),
    image_type  TEXT,
    file_path   TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_entry_data ON entry USING gin(data);
