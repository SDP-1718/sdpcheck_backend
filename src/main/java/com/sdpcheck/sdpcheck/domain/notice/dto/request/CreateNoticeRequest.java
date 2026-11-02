package com.sdpcheck.sdpcheck.domain.notice.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public record CreateNoticeRequest(
        @NotBlank String title,
        @NotBlank String content,
        @JsonSetter(nulls = Nulls.FAIL) Boolean isPinned,
        @JsonSetter(nulls = Nulls.FAIL) List<UUID> imageIds
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("지원하지 않는 필드: " + name);
    }
}
