package com.sdpcheck.sdpcheck.domain.schedule.dto.response;

import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ScheduleSummary(
        UUID id,
        ScheduleType type,
        String title,
        OffsetDateTime startsAt,
        String location
) {
}
