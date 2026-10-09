CREATE TABLE semesters (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (length(btrim(name)) > 0),
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    is_current BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT ck_semesters_dates CHECK (starts_on <= ends_on)
);

CREATE UNIQUE INDEX ux_semesters_current ON semesters (is_current) WHERE is_current = true;

ALTER TABLE study_sessions
    ADD COLUMN semester_id UUID REFERENCES semesters(id),
    ADD COLUMN is_regular BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN ended_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_study_sessions_lifecycle CHECK (
        ended_at IS NULL OR (started_at IS NOT NULL AND ended_at >= started_at)
    );

CREATE INDEX ix_study_sessions_attendance_history ON study_sessions (semester_id, starts_at DESC, id DESC)
    WHERE is_regular = true AND ended_at IS NOT NULL AND cancelled_at IS NULL AND deleted_at IS NULL;

CREATE TABLE session_attendance_targets (
    session_id UUID NOT NULL REFERENCES study_sessions(id),
    member_id BIGINT NOT NULL REFERENCES members(id),
    PRIMARY KEY (session_id, member_id)
);

CREATE INDEX ix_session_attendance_targets_member ON session_attendance_targets (member_id, session_id);

CREATE TABLE attendances (
    session_id UUID NOT NULL,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PRESENT', 'LATE')),
    checked_in_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (session_id, member_id),
    CONSTRAINT fk_attendances_target FOREIGN KEY (session_id, member_id)
        REFERENCES session_attendance_targets (session_id, member_id)
);
