package com.sdpcheck.sdpcheck.domain.schedule.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        String type,
        String title,
        OffsetDateTime startsAt,
        String location,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        int version
) {
}
