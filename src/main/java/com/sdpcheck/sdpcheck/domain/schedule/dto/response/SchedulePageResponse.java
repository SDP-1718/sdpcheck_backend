package com.sdpcheck.sdpcheck.domain.schedule.dto.response;

import java.util.List;

public record SchedulePageResponse(
        List<ScheduleSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public SchedulePageResponse {
        items = List.copyOf(items);
    }
}
