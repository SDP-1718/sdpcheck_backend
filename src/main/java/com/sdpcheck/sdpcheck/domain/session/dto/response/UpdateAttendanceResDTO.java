package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UpdateAttendanceResDTO(
		UUID sessionId,
		Long memberId,
		String name,
		Integer generation,
		AttendanceStatus status,
		OffsetDateTime checkedInAt
) {
}
