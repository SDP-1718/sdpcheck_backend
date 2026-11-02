CREATE TABLE notices (
    id UUID PRIMARY KEY,
    title TEXT NOT NULL CHECK (length(btrim(title)) > 0),
    content TEXT NOT NULL CHECK (length(btrim(content)) > 0),
    is_pinned BOOLEAN NOT NULL DEFAULT false,
    author_id BIGINT NOT NULL REFERENCES members(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_notices_deleted_not_pinned CHECK (deleted_at IS NULL OR NOT is_pinned)
);

CREATE UNIQUE INDEX uq_notices_active_pin ON notices (is_pinned)
    WHERE is_pinned = true AND deleted_at IS NULL;

CREATE INDEX ix_notices_active_created ON notices (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;
