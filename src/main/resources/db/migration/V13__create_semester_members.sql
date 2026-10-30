CREATE TABLE semester_members (
    semester_id UUID NOT NULL REFERENCES semesters(id),
    member_id BIGINT NOT NULL REFERENCES members(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (semester_id, member_id)
);

CREATE INDEX ix_semester_members_member ON semester_members (member_id, semester_id);
