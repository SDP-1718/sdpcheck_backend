package com.sdpcheck.sdpcheck.domain.statistics.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class StatisticsQueryRepository {
    private static final int ACTIVE_ATTENDANCE_RATE = 70;
    private final NamedParameterJdbcTemplate jdbc;

    public StatisticsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public StatisticsAggregate aggregate(UUID semesterId) {
        return jdbc.queryForObject("""
                WITH eligible_sessions AS (
                    SELECT id
                    FROM study_sessions
                    WHERE semester_id = :semesterId AND is_regular = true
                          AND ended_at IS NOT NULL AND cancelled_at IS NULL AND deleted_at IS NULL
                ), member_attendance AS (
                    SELECT t.member_id, count(*) AS total_sessions,
                           count(*) FILTER (WHERE a.status IN ('PRESENT', 'LATE')) AS attended_sessions
                    FROM eligible_sessions s
                    JOIN session_attendance_targets t ON t.session_id = s.id
                    JOIN semester_members sm ON sm.semester_id = :semesterId AND sm.member_id = t.member_id
                    LEFT JOIN attendances a ON a.session_id = t.session_id AND a.member_id = t.member_id
                    GROUP BY t.member_id
                )
                SELECT round(avg(100.0 * a.attended_sessions / NULLIF(a.total_sessions, 0)))::integer
                           AS average_attendance_rate,
                       (SELECT count(*) FROM eligible_sessions) AS total_session_count,
                       count(*) FILTER (
                           WHERE a.total_sessions > 0
                                 AND a.attended_sessions::numeric * 100 >= a.total_sessions::numeric * :activeRate
                       ) AS active_member_count,
                       count(*) AS total_member_count
                FROM semester_members sm
                LEFT JOIN member_attendance a ON a.member_id = sm.member_id
                WHERE sm.semester_id = :semesterId
                """, new MapSqlParameterSource("semesterId", semesterId).addValue("activeRate", ACTIVE_ATTENDANCE_RATE),
                (rs, rowNum) -> new StatisticsAggregate(rs.getObject("average_attendance_rate", Integer.class),
                        rs.getLong("total_session_count"), rs.getLong("active_member_count"),
                        rs.getLong("total_member_count")));
    }

    public record StatisticsAggregate(
            Integer averageAttendanceRate,
            long totalSessionCount,
            long activeMemberCount,
            long totalMemberCount
    ) {
    }
}
