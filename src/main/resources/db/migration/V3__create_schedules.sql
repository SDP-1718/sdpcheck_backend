CREATE TABLE events (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL CHECK (length(btrim(title)) > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(200),
    created_by BIGINT NOT NULL REFERENCES members(id),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_events_active_starts_at ON events (starts_at, id)
    WHERE deleted_at IS NULL;

-- The session domain can extend this shared table in its own migration.
CREATE TABLE study_sessions (
    id UUID PRIMARY KEY,
    title VARCHAR(100) NOT NULL CHECK (length(btrim(title)) > 0),
    starts_at TIMESTAMPTZ NOT NULL,
    location VARCHAR(200),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX ix_study_sessions_active_starts_at ON study_sessions (starts_at, id)
    WHERE deleted_at IS NULL;

CREATE VIEW schedule_items AS
SELECT id, 'EVENT'::text AS type, title, starts_at, location, version
FROM events WHERE deleted_at IS NULL
UNION ALL
SELECT id, 'SESSION'::text AS type, title, starts_at, location, NULL::integer AS version
FROM study_sessions WHERE deleted_at IS NULL;
