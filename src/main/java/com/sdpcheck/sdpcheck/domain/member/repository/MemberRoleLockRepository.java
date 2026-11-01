package com.sdpcheck.sdpcheck.domain.member.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRoleLockRepository {
    private static final long ROLE_CHANGE_LOCK_KEY = 2026103056L;
    private final JdbcTemplate jdbc;

    public MemberRoleLockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockRoleChanges() {
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> { }, ROLE_CHANGE_LOCK_KEY);
    }
}
