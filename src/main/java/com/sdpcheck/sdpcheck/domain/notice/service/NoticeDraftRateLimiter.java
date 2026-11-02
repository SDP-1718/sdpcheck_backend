package com.sdpcheck.sdpcheck.domain.notice.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class NoticeDraftRateLimiter {
    private static final int LIMIT = 10;
    private static final Duration WINDOW = Duration.ofSeconds(60);
    private final NamedParameterJdbcTemplate jdbc;

    public NoticeDraftRateLimiter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public Permit acquire(long memberId) {
        var params = new MapSqlParameterSource("memberId", memberId);
        // A separate advisory-lock namespace serializes this user's quota across app instances.
        // Very large member IDs may share a lock slot, but their quotas remain independent.
        jdbc.query("""
                SELECT pg_advisory_xact_lock(27093002, CAST(:memberId % 2147483647 AS INTEGER))
                """, params, rs -> { });
        OffsetDateTime now = jdbc.getJdbcTemplate().queryForObject(
                "SELECT clock_timestamp()", OffsetDateTime.class);
        params.addValue("now", now).addValue("cutoff", now.minus(WINDOW));
        jdbc.update("""
                DELETE FROM notice_draft_requests
                WHERE member_id = :memberId AND requested_at <= :cutoff
                """, params);
        WindowUsage usage = jdbc.queryForObject("""
                SELECT count(*) AS used, min(requested_at) AS oldest
                FROM notice_draft_requests WHERE member_id = :memberId
                """, params, (rs, rowNum) -> new WindowUsage(rs.getInt("used"),
                rs.getObject("oldest", OffsetDateTime.class)));
        if (usage.used() >= LIMIT) {
            long remainingNanos = Duration.between(now, usage.oldest().plus(WINDOW)).toNanos();
            int retryAfter = (int) Math.max(1, (remainingNanos + 999_999_999L) / 1_000_000_000L);
            return new Permit(false, retryAfter);
        }
        jdbc.update("""
                INSERT INTO notice_draft_requests (member_id, requested_at)
                VALUES (:memberId, :now)
                """, params);
        return new Permit(true, 0);
    }

    public record Permit(boolean allowed, int retryAfterSeconds) {
    }

    private record WindowUsage(int used, OffsetDateTime oldest) {
    }
}
