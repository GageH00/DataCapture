-- Comic Data Capture — schema
-- Compatible with H2 (embedded, bundled) and PostgreSQL (enterprise deployment).
--
-- H2 differences from PostgreSQL:
--   SERIAL        → AUTO_INCREMENT (H2 syntax; Flyway applies the right file per DB)
--   JSONB         → JSON           (H2 stores as TEXT, indexed via function index)
--   GIN index     → standard index on JSON_VALUE expression (H2 workaround)
--
-- Two migration files are maintained:
--   src/main/resources/db/migration/V1__schema_h2.sql        (default / bundled)
--   src/main/resources/db/migration/V1__schema_postgres.sql  (enterprise override)
-- Flyway picks the right one based on the active datasource URL.

CREATE TABLE IF NOT EXISTS sessions (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    started_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS batches (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    session_id INT REFERENCES sessions(id),
    item_label VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW()
);

-- Snapshot of the field schema active when a session was started.
-- Survives profile changes — you can always reconstruct what fields
-- were in use for any historical session.
CREATE TABLE IF NOT EXISTS field_definitions (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    session_id  INT REFERENCES sessions(id),
    field_order INT NOT NULL,
    label       VARCHAR(255) NOT NULL,
    db_key      VARCHAR(255) NOT NULL,
    field_type  VARCHAR(50)  NOT NULL,
    required    BOOLEAN DEFAULT FALSE,
    options     VARCHAR(2000)           -- comma-separated, for DROPDOWN fields
);

-- All operator-entered field values stored as a JSON object.
-- Keys match the db_key values in field_definitions for this session.
-- Example: {"series_name":"Batman","variant":"true","publisher":"DC"}
CREATE TABLE IF NOT EXISTS entry (
    id           INT AUTO_INCREMENT PRIMARY KEY,
    batch_id     INT REFERENCES batches(id),
    captured_at  TIMESTAMP DEFAULT NOW(),
    data         VARCHAR(32000) NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS images (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    batch_id    INT REFERENCES batches(id),
    entry_id    INT REFERENCES entry(id),
    image_type  VARCHAR(50),
    file_path   VARCHAR(1000) NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);
