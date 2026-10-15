package com.sdpcheck.sdpcheck.domain.insight.repository;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.insight.dto.AttendanceSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class AttendanceInsightQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    private static final String ATTENDANCE_FROM = """
            FROM session_attendance_targets t
            JOIN study_sessions s ON s.id = t.session_id
            LEFT JOIN attendances a
                ON a.session_id = t.session_id
                AND a.member_id = t.member_id
            WHERE t.member_id = :memberId
                AND s.is_regular = true
                AND s.ended_at IS NOT NULL
                AND s.cancelled_at IS NULL
                AND s.deleted_at IS NULL
            """;

    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public AttendanceSummary findSummary(Long memberId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("memberId", memberId);

        String countSql = """
                SELECT
                    COUNT(*) AS total_count,
                    COUNT(*) FILTER (
                        WHERE a.status = 'PRESENT'
                    ) AS present_count,
                    COUNT(*) FILTER (
                        WHERE a.status = 'LATE'
                    ) AS late_count,
                    COUNT(*) FILTER (
                        WHERE a.status IS NULL
                    ) AS absent_count
                """ + ATTENDANCE_FROM;

        AttendanceCounts counts = jdbc.queryForObject(
                countSql,
                params,
                (rs, rowNum) -> new AttendanceCounts(
                        rs.getLong("total_count"),
                        rs.getLong("present_count"),
                        rs.getLong("late_count"),
                        rs.getLong("absent_count")
                )
        );

        String recentSql = """
                SELECT COALESCE(a.status, 'ABSENT') AS status
                """ + ATTENDANCE_FROM + """
                ORDER BY s.starts_at DESC, s.id DESC
                LIMIT 4
                """;

        List<AttendanceStatus> recentStatuses = jdbc.query(
                recentSql,
                params,
                (rs, rowNum) ->
                        AttendanceStatus.valueOf(rs.getString("status"))
        );

        double attendanceRate = counts.totalCount() == 0
                ? 0.0
                : (counts.presentCount() + counts.lateCount())
                * 100.0 / counts.totalCount();

        return new AttendanceSummary(
                counts.totalCount(),
                counts.presentCount(),
                counts.lateCount(),
                counts.absentCount(),
                attendanceRate,
                recentStatuses
        );
    }

    private record AttendanceCounts(
            long totalCount,
            long presentCount,
            long lateCount,
            long absentCount
    ) {
    }
}
