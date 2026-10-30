package com.sdpcheck.sdpcheck.domain.statistics.dto.response;

import java.util.List;

public record MemberAttendanceStatisticsResponse(
        String periodCode,
        List<MemberAttendanceStatisticsItem> members,
        int page,
        int size,
        boolean hasNext
) {
}
