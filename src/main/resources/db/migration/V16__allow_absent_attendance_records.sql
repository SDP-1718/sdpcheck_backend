ALTER TABLE attendances
    DROP CONSTRAINT attendances_status_check,
    ALTER COLUMN checked_in_at DROP NOT NULL,
    ADD CONSTRAINT ck_attendances_status CHECK (status IN ('PRESENT', 'LATE', 'ABSENT')),
    ADD CONSTRAINT ck_attendances_checked_in_at CHECK ((status = 'ABSENT') = (checked_in_at IS NULL));
