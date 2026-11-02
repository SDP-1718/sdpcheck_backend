package com.sdpcheck.sdpcheck.domain.home.repository;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.home.dto.response.HomeResponse.MemberInfo;
import com.sdpcheck.sdpcheck.domain.home.dto.response.HomeResponse.NoticePreview;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HomeQueryRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final NamedParameterJdbcTemplate jdbc;

    public HomeQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<MemberInfo> findMember(long memberId) {
        return jdbc.query("SELECT name, generation, role FROM members WHERE id = :memberId",
                new MapSqlParameterSource("memberId", memberId), (rs, rowNum) -> new MemberInfo(
                        rs.getString("name"), rs.getInt("generation"), Role.valueOf(rs.getString("role"))))
                .stream().findFirst();
    }

    public Optional<SessionCandidate> findSession(long memberId, OffsetDateTime serverTime) {
        var startOfDay = serverTime.atZoneSameInstant(SEOUL).toLocalDate().atStartOfDay(SEOUL).toOffsetDateTime();
        var params = new MapSqlParameterSource("memberId", memberId)
                .addValue("now", serverTime).addValue("startOfDay", startOfDay);
        return jdbc.query("""
                SELECT s.id, s.title, s.starts_at, s.started_at, s.ended_at,
                       a.status AS attendance_status, a.checked_in_at
                FROM study_sessions s
                JOIN session_attendance_targets t ON t.session_id = s.id AND t.member_id = :memberId
                LEFT JOIN attendances a ON a.session_id = s.id AND a.member_id = t.member_id
                WHERE s.deleted_at IS NULL AND s.cancelled_at IS NULL
                      AND s.is_published = true AND s.is_regular = true
                      AND (
                          (s.started_at <= :now AND s.ended_at IS NULL)
                          OR (s.ended_at >= :startOfDay AND s.ended_at <= :now)
                          OR (s.started_at IS NULL AND s.ended_at IS NULL AND s.starts_at >= :now)
                      )
                ORDER BY CASE
                             WHEN s.started_at IS NOT NULL AND s.ended_at IS NULL THEN 0
                             WHEN s.ended_at IS NOT NULL THEN 1
                             ELSE 2
                         END,
                         CASE WHEN s.ended_at IS NULL THEN s.started_at END DESC,
                         s.ended_at DESC,
                         CASE WHEN s.started_at IS NULL THEN s.starts_at END ASC,
                         s.id ASC
                LIMIT 1
                """, params, (rs, rowNum) -> new SessionCandidate(
                        rs.getObject("id", UUID.class), rs.getString("title"),
                        seoul(rs.getObject("starts_at", OffsetDateTime.class)),
                        seoul(rs.getObject("started_at", OffsetDateTime.class)),
                        seoul(rs.getObject("ended_at", OffsetDateTime.class)),
                        rs.getString("attendance_status") == null ? null
                                : AttendanceStatus.valueOf(rs.getString("attendance_status")),
                        seoul(rs.getObject("checked_in_at", OffsetDateTime.class))))
                .stream().findFirst();
    }

    public List<NoticePreview> findLatestNotices() {
        return jdbc.getJdbcTemplate().query("""
                SELECT id, title, created_at
                FROM notices
                WHERE deleted_at IS NULL AND is_published = true
                ORDER BY created_at DESC, id DESC
                LIMIT 2
                """, (rs, rowNum) -> new NoticePreview(rs.getObject("id", UUID.class),
                rs.getString("title"), seoul(rs.getObject("created_at", OffsetDateTime.class))));
    }

    public List<ScheduleSummary> findUpcomingSchedules(OffsetDateTime serverTime) {
        return jdbc.query("""
                SELECT id, type, title, starts_at, location
                FROM (
                    SELECT id, 'SESSION'::text AS type, 0 AS type_order, title, starts_at, location
                    FROM study_sessions
                    WHERE deleted_at IS NULL AND cancelled_at IS NULL AND is_published = true
                          AND started_at IS NULL AND ended_at IS NULL AND starts_at > :now
                    UNION ALL
                    SELECT id, 'GENERAL'::text AS type, 1 AS type_order, title, starts_at, location
                    FROM events
                    WHERE deleted_at IS NULL AND cancelled_at IS NULL AND is_published = true
                          AND starts_at > :now
                ) upcoming
                ORDER BY starts_at ASC, type_order ASC, id ASC
                LIMIT 2
                """, new MapSqlParameterSource("now", serverTime), (rs, rowNum) -> new ScheduleSummary(
                rs.getObject("id", UUID.class), ScheduleType.valueOf(rs.getString("type")),
                rs.getString("title"), seoul(rs.getObject("starts_at", OffsetDateTime.class)),
                rs.getString("location")));
    }

    private static OffsetDateTime seoul(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }

    public record SessionCandidate(
            UUID id,
            String title,
            OffsetDateTime startsAt,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            AttendanceStatus attendanceStatus,
            OffsetDateTime checkedInAt
    ) {
    }
}
