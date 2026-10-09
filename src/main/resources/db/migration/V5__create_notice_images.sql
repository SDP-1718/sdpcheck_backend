CREATE TABLE uploaded_files (
    id UUID PRIMARY KEY,
    owner_id BIGINT NOT NULL REFERENCES members(id),
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(30) NOT NULL,
    size_bytes BIGINT NOT NULL,
    data BYTEA NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    attach_before TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (status IN ('PENDING', 'ATTACHED')),
    CHECK (size_bytes BETWEEN 1 AND 5242880),
    CHECK (octet_length(data) = size_bytes)
);

CREATE INDEX ix_uploaded_files_pending_expiry ON uploaded_files (attach_before)
    WHERE status = 'PENDING';

CREATE TABLE notice_images (
    notice_id UUID NOT NULL REFERENCES notices(id),
    file_id UUID NOT NULL UNIQUE REFERENCES uploaded_files(id),
    position INTEGER NOT NULL CHECK (position >= 0),
    PRIMARY KEY (notice_id, file_id),
    UNIQUE (notice_id, position)
);
