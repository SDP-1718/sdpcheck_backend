package com.sdpcheck.sdpcheck.domain.statistics.dto.response;

public record MemberAttendanceStatisticsItem(
        long memberId,
        String name,
        int generation,
        long presentCount,
        long lateCount,
        long absentCount,
        long totalSessionCount,
        Integer attendanceRate,
        Boolean isAtRisk
) {
}
