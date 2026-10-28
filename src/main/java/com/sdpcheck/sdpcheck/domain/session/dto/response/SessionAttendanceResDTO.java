package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.session.enums.LiveAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record SessionAttendanceResDTO(
		UUID sessionId,
		SessionStatus sessionStatus,
		OffsetDateTime checkInClosesAt,
		Summary summary,
		List<MemberAttendance> members
) {

	public SessionAttendanceResDTO {
		members = List.copyOf(members);
	}

	public record Summary(
			int totalCount,
			int presentCount,
			int lateCount,
			int notCheckedInCount,
			int absentCount,
			double attendanceRate
	) {
	}

	public record MemberAttendance(
			Long memberId,
			String name,
			Integer generation,
			LiveAttendanceStatus status,
			OffsetDateTime checkedInAt
	) {
	}
}
