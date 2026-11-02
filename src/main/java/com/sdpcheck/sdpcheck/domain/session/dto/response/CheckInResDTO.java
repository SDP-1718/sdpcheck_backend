package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CheckInResDTO(
		long checkInId,
		UUID sessionId,
		AttendanceStatus attendanceStatus,
		OffsetDateTime checkedInAt
) {
}
