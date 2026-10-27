package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoticeSummary(
        UUID id,
        String title,
        boolean isPinned,
        NoticeAuthor author,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
