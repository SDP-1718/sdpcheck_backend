package com.sdpcheck.sdpcheck.domain.attendance.dto.response;

public record AttendanceSummaryResponse(
        String periodCode,
        Integer attendanceRate,
        long presentCount,
        long lateCount,
        long absentCount,
        long totalSessionCount
) {
}
