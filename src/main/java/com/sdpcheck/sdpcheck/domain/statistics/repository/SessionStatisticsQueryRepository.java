package com.sdpcheck.sdpcheck.domain.statistics.repository;

import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsItem;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Repository
public class SessionStatisticsQueryRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final NamedParameterJdbcTemplate jdbc;

    public SessionStatisticsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SessionAttendanceStatisticsItem> findSessionStatistics(UUID semesterId) {
        return jdbc.query("""
                WITH session_statistics AS (
                    SELECT s.id AS session_id, s.title AS session_title, s.starts_at,
                           count(t.member_id) AS total_count,
                           count(t.member_id) FILTER (WHERE a.status = 'PRESENT') AS present_count,
                           count(t.member_id) FILTER (WHERE a.status = 'LATE') AS late_count
                    FROM study_sessions s
                    LEFT JOIN session_attendance_targets t ON t.session_id = s.id
                    LEFT JOIN attendances a ON a.session_id = t.session_id AND a.member_id = t.member_id
                    WHERE s.semester_id = :semesterId AND s.is_regular = true
                          AND s.ended_at IS NOT NULL AND s.cancelled_at IS NULL AND s.deleted_at IS NULL
                    GROUP BY s.id, s.title, s.starts_at
                )
                SELECT session_id, session_title, starts_at, total_count, present_count, late_count,
                       total_count - present_count - late_count AS absent_count,
                       round(100.0 * (present_count + late_count) / NULLIF(total_count, 0))::integer AS attendance_rate
                FROM session_statistics
                ORDER BY starts_at ASC, session_id ASC
                """, new MapSqlParameterSource("semesterId", semesterId),
                (rs, rowNum) -> new SessionAttendanceStatisticsItem(
                        rs.getObject("session_id", UUID.class), rs.getString("session_title"),
                        rs.getObject("starts_at", OffsetDateTime.class).atZoneSameInstant(SEOUL).toOffsetDateTime(),
                        rs.getLong("total_count"), rs.getLong("present_count"), rs.getLong("late_count"),
                        rs.getLong("absent_count"), rs.getObject("attendance_rate", Integer.class)));
    }
}
