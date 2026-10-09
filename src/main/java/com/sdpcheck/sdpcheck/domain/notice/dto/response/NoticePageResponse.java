package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import java.util.List;

public record NoticePageResponse(
        List<NoticeSummary> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public NoticePageResponse {
        items = List.copyOf(items);
    }
}
