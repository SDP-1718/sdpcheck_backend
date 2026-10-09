ALTER TABLE notices
    ADD COLUMN is_published BOOLEAN NOT NULL DEFAULT true;

CREATE INDEX ix_notices_visible_order ON notices (is_pinned DESC, created_at DESC, id DESC)
    WHERE deleted_at IS NULL AND is_published = true;
