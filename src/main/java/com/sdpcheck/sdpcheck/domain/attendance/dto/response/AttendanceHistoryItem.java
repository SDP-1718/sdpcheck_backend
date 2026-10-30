package com.sdpcheck.sdpcheck.domain.attendance.dto.response;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AttendanceHistoryItem(
        UUID sessionId,
        String sessionTitle,
        OffsetDateTime startsAt,
        AttendanceStatus attendanceStatus,
        OffsetDateTime checkedInAt
) {
}
