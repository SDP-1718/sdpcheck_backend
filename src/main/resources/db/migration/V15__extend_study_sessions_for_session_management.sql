ALTER TABLE study_sessions
    ADD COLUMN ends_at TIMESTAMPTZ,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD CONSTRAINT ck_study_sessions_schedule CHECK (ends_at IS NULL OR ends_at > starts_at);
