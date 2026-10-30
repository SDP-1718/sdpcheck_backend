package com.sdpcheck.sdpcheck.domain.session.repository;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionAttendanceRepository {

	private static final String TARGET_ATTENDANCE_SELECT = """
			SELECT m.id, m.name, m.generation, a.status, a.checked_in_at
			FROM session_attendance_targets t
			JOIN members m ON m.id = t.member_id
			LEFT JOIN attendances a ON a.session_id = t.session_id AND a.member_id = t.member_id
			WHERE t.session_id = :sessionId
			""";
	private static final RowMapper<TargetAttendance> TARGET_ATTENDANCE_MAPPER = (rs, rowNum) -> {
		String status = rs.getString("status");
		return new TargetAttendance(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getInt("generation"),
				status == null ? null : AttendanceStatus.valueOf(status),
				rs.getObject("checked_in_at", OffsetDateTime.class)
		);
	};

	private final NamedParameterJdbcTemplate jdbc;

	public SessionAttendanceRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void registerAllMembersAsTargets(UUID sessionId) {
		jdbc.update("""
				INSERT INTO session_attendance_targets (session_id, member_id)
				SELECT :sessionId, id FROM members WHERE role = 'MEMBER'
				ON CONFLICT DO NOTHING
				""", new MapSqlParameterSource("sessionId", sessionId));
	}

	public boolean isTarget(UUID sessionId, long memberId) {
		Boolean exists = jdbc.queryForObject("""
				SELECT EXISTS (
					SELECT 1 FROM session_attendance_targets
					WHERE session_id = :sessionId AND member_id = :memberId
				)
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId), Boolean.class);
		return Boolean.TRUE.equals(exists);
	}

	public boolean isTargetForUpdate(UUID sessionId, long memberId) {
		return !jdbc.query("""
				SELECT member_id
				FROM session_attendance_targets
				WHERE session_id = :sessionId AND member_id = :memberId
				FOR KEY SHARE
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId),
				(rs, rowNum) -> rs.getLong("member_id")).isEmpty();
	}

	public List<TargetAttendance> findTargetAttendances(UUID sessionId) {
		return jdbc.query(TARGET_ATTENDANCE_SELECT + """
				ORDER BY m.generation ASC, m.name ASC, m.id ASC
				""", new MapSqlParameterSource("sessionId", sessionId), TARGET_ATTENDANCE_MAPPER);
	}

	public Optional<TargetAttendance> findTargetAttendance(UUID sessionId, long memberId) {
		return jdbc.query(TARGET_ATTENDANCE_SELECT + """
				AND t.member_id = :memberId
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId), TARGET_ATTENDANCE_MAPPER).stream().findFirst();
	}

	// Keeps the original check-in time when switching between PRESENT and LATE; ABSENT has none.
	public void upsertAttendanceStatus(UUID sessionId, long memberId, AttendanceStatus status, OffsetDateTime checkedInAt) {
		jdbc.update("""
				INSERT INTO attendances (session_id, member_id, status, checked_in_at)
				VALUES (:sessionId, :memberId, :status, :checkedInAt)
				ON CONFLICT (session_id, member_id) DO UPDATE SET
					status = EXCLUDED.status,
					checked_in_at = CASE
						WHEN EXCLUDED.status = 'ABSENT' THEN NULL
						ELSE COALESCE(attendances.checked_in_at, EXCLUDED.checked_in_at)
					END
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId)
				.addValue("status", status.name())
				.addValue("checkedInAt", status == AttendanceStatus.ABSENT ? null : checkedInAt));
	}

	public void markRemainingTargetsAbsent(UUID sessionId) {
		jdbc.update("""
				INSERT INTO attendances (session_id, member_id, status, checked_in_at)
				SELECT session_id, member_id, 'ABSENT', NULL
				FROM session_attendance_targets
				WHERE session_id = :sessionId
				ON CONFLICT DO NOTHING
				""", new MapSqlParameterSource("sessionId", sessionId));
	}

	public Optional<AttendanceStatus> findAttendanceStatus(UUID sessionId, long memberId) {
		return jdbc.query("""
				SELECT status FROM attendances WHERE session_id = :sessionId AND member_id = :memberId
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId), (rs, rowNum) -> AttendanceStatus.valueOf(rs.getString("status")))
				.stream().findFirst();
	}

	public Optional<Long> insertAttendance(UUID sessionId, long memberId, AttendanceStatus status, OffsetDateTime checkedInAt) {
		return jdbc.query("""
				INSERT INTO attendances (session_id, member_id, status, checked_in_at)
				VALUES (:sessionId, :memberId, :status, :checkedInAt)
				ON CONFLICT (session_id, member_id) DO NOTHING
				RETURNING id
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId)
				.addValue("status", status.name())
				.addValue("checkedInAt", checkedInAt),
				(rs, rowNum) -> rs.getLong("id")).stream().findFirst();
	}

	public record TargetAttendance(
			long memberId,
			String name,
			int generation,
			AttendanceStatus status,
			OffsetDateTime checkedInAt
	) {
	}
}
