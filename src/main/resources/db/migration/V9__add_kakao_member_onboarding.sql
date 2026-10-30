ALTER TABLE members
    ADD COLUMN kakao_id BIGINT,
    ADD COLUMN onboarding_completed BOOLEAN NOT NULL DEFAULT TRUE,
    ALTER COLUMN role DROP NOT NULL,
    ALTER COLUMN login_id DROP NOT NULL,
    ALTER COLUMN password DROP NOT NULL,
    ALTER COLUMN name DROP NOT NULL,
    ALTER COLUMN generation DROP NOT NULL;

ALTER TABLE members
    ADD CONSTRAINT uk_members_kakao_id UNIQUE (kakao_id),
    ADD CONSTRAINT ck_members_kakao_id_positive
        CHECK (kakao_id IS NULL OR kakao_id > 0),
    ADD CONSTRAINT ck_members_auth_identity
        CHECK (
            (login_id IS NOT NULL AND password IS NOT NULL)
            OR
            (kakao_id IS NOT NULL AND login_id IS NULL AND password IS NULL)
        ),
    ADD CONSTRAINT ck_members_onboarding_state
        CHECK (
            (
                onboarding_completed = TRUE
                AND role IS NOT NULL
                AND role IN ('MEMBER', 'ADMIN')
                AND name IS NOT NULL
                AND generation IS NOT NULL
            )
            OR
            (
                onboarding_completed = FALSE
                AND kakao_id IS NOT NULL
                AND role IS NULL
            )
        );
