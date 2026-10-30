package com.sdpcheck.sdpcheck.domain.session.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionQrResDTO(
		UUID sessionId,
		String qrToken,
		OffsetDateTime expiresAt
) {
}
