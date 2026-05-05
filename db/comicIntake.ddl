CREATE TABLE sessions (
    id         SERIAL PRIMARY KEY,
    started_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE batches (
    id         SERIAL PRIMARY KEY,
    session_id INT REFERENCES sessions(id),
    item_label TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

-- field_definitions captures the exact field schema used during a session.
-- This gives a permanent audit trail: you can always reconstruct what fields
-- were active for any historical session, even after the profile changes.
CREATE TABLE field_definitions (
    id          SERIAL PRIMARY KEY,
    session_id  INT REFERENCES sessions(id),
    field_order INT NOT NULL,
    label       TEXT NOT NULL,
    db_key      TEXT NOT NULL,
    field_type  TEXT NOT NULL,
    required    BOOLEAN DEFAULT FALSE,
    options     TEXT
);

-- entry.data stores all operator-entered field values as JSONB.
-- Keys in data correspond to db_key values in field_definitions.
-- Comic deployment: {"series_name":"Batman","variant":true,"publisher":"DC"}
-- Other deployment: {"item_title":"Vase","condition":"Fine","era":"1920s"}
CREATE TABLE entry (
    id           SERIAL PRIMARY KEY,
    batch_id     INT REFERENCES batches(id),
    captured_at  TIMESTAMP DEFAULT NOW(),
    data         JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE images (
    id          SERIAL PRIMARY KEY,
    batch_id    INT REFERENCES batches(id),
    entry_id    INT REFERENCES entry(id),
    image_type  TEXT,
    file_path   TEXT NOT NULL,
    created_at  TIMESTAMP DEFAULT NOW()
);

-- Efficient querying of any field value across all entries
CREATE INDEX idx_entry_data ON entry USING gin(data);
