ALTER TABLE semesters
    ADD COLUMN period_code VARCHAR(50),
    ADD CONSTRAINT ck_semesters_period_code CHECK (
        period_code IS NULL OR (period_code = btrim(period_code) AND length(period_code) > 0)
    );

CREATE UNIQUE INDEX ux_semesters_period_code ON semesters (period_code)
    WHERE period_code IS NOT NULL;
