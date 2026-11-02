CREATE TABLE notice_draft_requests (
    member_id BIGINT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    requested_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_notice_draft_requests_member_time
    ON notice_draft_requests (member_id, requested_at);
