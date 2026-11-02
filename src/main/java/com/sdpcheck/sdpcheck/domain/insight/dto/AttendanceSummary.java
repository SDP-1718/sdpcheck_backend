package com.sdpcheck.sdpcheck.domain.insight.dto;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;

import java.util.List;

public record AttendanceSummary(
        long totalCount,
        long presentCount,
        long lateCount,
        long absentCount,
        double attendanceRate,
        List<AttendanceStatus> recentStatuses
) {
}