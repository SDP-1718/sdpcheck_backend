package com.sdpcheck.sdpcheck.domain.attendance.dto.response;

import java.util.List;

public record AttendanceHistoryPageResponse(
        List<AttendanceHistoryItem> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public AttendanceHistoryPageResponse {
        items = List.copyOf(items);
    }
}
