package com.sdpcheck.sdpcheck.domain.attendance.repository;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryItem;
import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AttendanceQueryRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String HISTORY_FROM = """
            FROM session_attendance_targets t
            JOIN study_sessions s ON s.id = t.session_id
            LEFT JOIN attendances a ON a.session_id = t.session_id AND a.member_id = t.member_id
            WHERE t.member_id = :memberId AND s.semester_id = :semesterId
                  AND s.is_regular = true AND s.ended_at IS NOT NULL
                  AND s.cancelled_at IS NULL AND s.deleted_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public AttendanceQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<UUID> findCurrentSemesterId() {
        return jdbc.getJdbcTemplate().query("SELECT id FROM semesters WHERE is_current = true",
                (rs, rowNum) -> rs.getObject("id", UUID.class)).stream().findFirst();
    }

    public long countHistory(long memberId, UUID semesterId) {
        Long count = jdbc.queryForObject("SELECT count(*)\n" + HISTORY_FROM,
                historyParams(memberId, semesterId), Long.class);
        return count == null ? 0 : count;
    }

    public List<AttendanceHistoryItem> findHistory(long memberId, UUID semesterId, int page, int size) {
        String sql = """
                SELECT s.id AS session_id, s.title AS session_title, s.starts_at,
                       COALESCE(a.status, 'ABSENT') AS attendance_status, a.checked_in_at
                %s
                ORDER BY s.starts_at DESC, s.id DESC
                LIMIT :limit OFFSET :offset
                """.formatted(HISTORY_FROM);
        var params = historyParams(memberId, semesterId)
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        return jdbc.query(sql, params, (rs, rowNum) -> new AttendanceHistoryItem(
                rs.getObject("session_id", UUID.class), rs.getString("session_title"),
                seoul(rs.getObject("starts_at", OffsetDateTime.class)),
                AttendanceStatus.valueOf(rs.getString("attendance_status")),
                seoul(rs.getObject("checked_in_at", OffsetDateTime.class))));
    }

    private static MapSqlParameterSource historyParams(long memberId, UUID semesterId) {
        return new MapSqlParameterSource("memberId", memberId).addValue("semesterId", semesterId);
    }

    private static OffsetDateTime seoul(OffsetDateTime timestamp) {
        return timestamp == null ? null : timestamp.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
