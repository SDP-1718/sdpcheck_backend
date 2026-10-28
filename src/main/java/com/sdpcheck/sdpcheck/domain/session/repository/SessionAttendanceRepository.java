package com.sdpcheck.sdpcheck.domain.session.repository;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionAttendanceRepository {

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

	public boolean insertAttendance(UUID sessionId, long memberId, AttendanceStatus status, OffsetDateTime checkedInAt) {
		int inserted = jdbc.update("""
				INSERT INTO attendances (session_id, member_id, status, checked_in_at)
				VALUES (:sessionId, :memberId, :status, :checkedInAt)
				ON CONFLICT DO NOTHING
				""", new MapSqlParameterSource()
				.addValue("sessionId", sessionId)
				.addValue("memberId", memberId)
				.addValue("status", status.name())
				.addValue("checkedInAt", checkedInAt));
		return inserted == 1;
	}
}
