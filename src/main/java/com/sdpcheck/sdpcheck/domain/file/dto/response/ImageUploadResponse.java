package com.sdpcheck.sdpcheck.domain.file.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ImageUploadResponse(UUID fileId, OffsetDateTime attachBefore) {
}
