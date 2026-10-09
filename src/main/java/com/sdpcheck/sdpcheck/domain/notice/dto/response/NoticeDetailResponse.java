package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NoticeDetailResponse(
        UUID id,
        String title,
        String content,
        boolean isPinned,
        NoticeAuthor author,
        List<String> imageUrls,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public NoticeDetailResponse {
        imageUrls = List.copyOf(imageUrls);
    }
}
