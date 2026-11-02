package com.sdpcheck.sdpcheck.domain.schedule.dto.response;

import java.util.List;

public record ScheduleListResponse(List<ScheduleItem> items, Page page) {
    public record Page(int page, int size, long totalElements, long totalPages) {
    }
}
