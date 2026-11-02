package com.sdpcheck.sdpcheck.domain.statistics.repository;

import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MemberStatisticsQueryRepository {
    private static final int AT_RISK_ATTENDANCE_RATE = 70;
    private final NamedParameterJdbcTemplate jdbc;

    public MemberStatisticsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MemberAttendanceStatisticsItem> findMembers(
            UUID semesterId, MemberStatisticsSort sort, int limit, long offset) {
        var params = parameters(semesterId).addValue("limit", limit).addValue("offset", offset);
        return query(statisticsSql(sort) + " LIMIT :limit OFFSET :offset", params);
    }

    public List<MemberAttendanceStatisticsItem> findAllMembers(UUID semesterId, MemberStatisticsSort sort) {
        return query(statisticsSql(sort), parameters(semesterId));
    }

    private String statisticsSql(MemberStatisticsSort sort) {
        String orderBy = switch (sort) {
            case ATTENDANCE_RATE_ASC -> "raw_rate ASC NULLS LAST, name ASC, member_id ASC";
            case ATTENDANCE_RATE_DESC -> "raw_rate DESC NULLS LAST, name ASC, member_id ASC";
            case NAME_ASC -> "name ASC, member_id ASC";
        };
        return """
                WITH eligible_sessions AS (
                    SELECT id
                    FROM study_sessions
                    WHERE semester_id = :semesterId AND is_regular = true
                          AND ended_at IS NOT NULL AND cancelled_at IS NULL AND deleted_at IS NULL
                ), member_attendance AS (
                    SELECT t.member_id, count(*) AS total_sessions,
                           count(*) FILTER (WHERE a.status = 'PRESENT') AS present_count,
                           count(*) FILTER (WHERE a.status = 'LATE') AS late_count
                    FROM eligible_sessions s
                    JOIN session_attendance_targets t ON t.session_id = s.id
                    JOIN semester_members sm ON sm.semester_id = :semesterId AND sm.member_id = t.member_id
                    LEFT JOIN attendances a ON a.session_id = t.session_id AND a.member_id = t.member_id
                    GROUP BY t.member_id
                ), member_statistics AS (
                    SELECT m.id AS member_id, m.name, m.generation,
                           COALESCE(a.total_sessions, 0) AS total_sessions,
                           COALESCE(a.present_count, 0) AS present_count,
                           COALESCE(a.late_count, 0) AS late_count,
                           100.0 * (a.present_count + a.late_count) / NULLIF(a.total_sessions, 0) AS raw_rate
                    FROM semester_members sm
                    JOIN members m ON m.id = sm.member_id
                    LEFT JOIN member_attendance a ON a.member_id = sm.member_id
                    WHERE sm.semester_id = :semesterId
                )
                SELECT member_id, name, generation, present_count, late_count,
                       total_sessions - present_count - late_count AS absent_count,
                       total_sessions, round(raw_rate)::integer AS attendance_rate,
                       CASE WHEN total_sessions = 0 THEN NULL
                            ELSE (present_count::numeric + late_count) * 100
                                     < total_sessions::numeric * :atRiskRate
                       END AS is_at_risk
                FROM member_statistics
                ORDER BY %s
                """.formatted(orderBy);
    }

    private MapSqlParameterSource parameters(UUID semesterId) {
        return new MapSqlParameterSource("semesterId", semesterId)
                .addValue("atRiskRate", AT_RISK_ATTENDANCE_RATE);
    }

    private List<MemberAttendanceStatisticsItem> query(String sql, MapSqlParameterSource params) {
        return jdbc.query(sql, params, (rs, rowNum) -> new MemberAttendanceStatisticsItem(
                rs.getLong("member_id"), rs.getString("name"), rs.getInt("generation"),
                rs.getLong("present_count"), rs.getLong("late_count"), rs.getLong("absent_count"),
                rs.getLong("total_sessions"), rs.getObject("attendance_rate", Integer.class),
                rs.getObject("is_at_risk", Boolean.class)));
    }
}
