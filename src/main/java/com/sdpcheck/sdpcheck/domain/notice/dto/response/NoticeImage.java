package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record NoticeImage(UUID fileId, String url, OffsetDateTime expiresAt) {
}
