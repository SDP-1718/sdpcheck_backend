package com.sdpcheck.sdpcheck.domain.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

public record CreateScheduleRequest(
        @NotBlank @Size(max = 100) String title,
        @NotNull OffsetDateTime startsAt,
        @Size(max = 200) String location
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("지원하지 않는 필드: " + name);
    }
}
