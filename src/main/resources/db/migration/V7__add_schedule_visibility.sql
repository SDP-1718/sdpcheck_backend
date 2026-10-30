ALTER TABLE events
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE study_sessions
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

CREATE INDEX ix_events_visible_starts_at ON events (starts_at, id)
    WHERE deleted_at IS NULL AND is_published = true AND cancelled_at IS NULL;

CREATE INDEX ix_study_sessions_visible_starts_at ON study_sessions (starts_at, id)
    WHERE deleted_at IS NULL AND is_published = true AND cancelled_at IS NULL;
