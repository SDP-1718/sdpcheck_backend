package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record NoticeResponse(
        UUID id,
        String title,
        String content,
        boolean isPinned,
        NoticeAuthor author,
        List<NoticeImage> images,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
