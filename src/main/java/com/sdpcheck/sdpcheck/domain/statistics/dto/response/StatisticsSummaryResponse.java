package com.sdpcheck.sdpcheck.domain.statistics.dto.response;

public record StatisticsSummaryResponse(
        String periodCode,
        Integer averageAttendanceRate,
        long totalSessionCount,
        long activeMemberCount,
        long totalMemberCount
) {
}
