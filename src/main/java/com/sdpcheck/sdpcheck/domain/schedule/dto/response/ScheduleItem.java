package com.sdpcheck.sdpcheck.domain.schedule.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleItem(
        UUID id,
        String type,
        String title,
        OffsetDateTime startsAt,
        String location,
        boolean canManage,
        Integer version
) {
}
