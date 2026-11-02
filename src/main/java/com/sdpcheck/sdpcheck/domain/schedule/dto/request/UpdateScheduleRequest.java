package com.sdpcheck.sdpcheck.domain.schedule.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.OffsetDateTime;

/** Presence flags distinguish an omitted property from an explicit JSON null. */
@Schema(description = "version과 수정할 필드를 최소 하나 전달합니다. location의 null은 장소 제거입니다.",
        requiredProperties = {"version"}, additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class UpdateScheduleRequest {
    @Schema(type = "string", minLength = 1, maxLength = 100, example = "운영진 회의")
    @Size(max = 100)
    private String title;
    @Schema(type = "string", format = "date-time", example = "2026-10-02T19:00:00+09:00")
    private OffsetDateTime startsAt;
    @Schema(types = {"string", "null"}, maxLength = 200, example = "학생회관 201호")
    @Size(max = 200)
    private String location;
    @Schema(type = "integer", minimum = "1", example = "1")
    @NotNull @Min(1)
    private Integer version;
    private boolean titlePresent;
    private boolean startsAtPresent;
    private boolean locationPresent;

    @Schema(implementation = String.class, type = "string", minLength = 1, maxLength = 100, example = "운영진 회의")
    @JsonSetter("title")
    public void setTitle(JsonNode value) {
        titlePresent = true;
        title = textOrNull(value);
        if (title != null) {
            title = title.strip();
        }
    }

    @Schema(implementation = OffsetDateTime.class, type = "string", format = "date-time", example = "2026-10-02T19:00:00+09:00")
    @JsonSetter("startsAt")
    public void setStartsAt(JsonNode value) {
        startsAtPresent = true;
        startsAt = null;
        String text = textOrNull(value);
        if (text != null) {
            if (!text.matches("\\d{4}-\\d{2}-\\d{2}[Tt]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:[Zz]|[+-]\\d{2}:\\d{2})")) {
                throw new IllegalArgumentException("startsAt은 UTC offset을 포함한 RFC 3339 형식이어야 합니다.");
            }
            startsAt = OffsetDateTime.parse(text);
        }
    }

    @Schema(implementation = String.class, types = {"string", "null"}, maxLength = 200, example = "학생회관 201호")
    @JsonSetter("location")
    public void setLocation(JsonNode value) {
        locationPresent = true;
        location = textOrNull(value);
    }

    @Schema(implementation = Integer.class, type = "integer", minimum = "1", example = "1")
    @JsonSetter("version")
    public void setVersion(JsonNode value) {
        if (value == null || value.isNull()) {
            version = null;
        } else if (value.isIntegralNumber() && value.canConvertToInt()) {
            version = value.intValue();
        } else {
            throw new IllegalArgumentException("version은 정수여야 합니다.");
        }
    }

    @AssertTrue(message = "수정할 필드를 하나 이상 전달해야 합니다.")
    @Schema(hidden = true)
    public boolean isChangePresent() {
        return titlePresent || startsAtPresent || locationPresent;
    }

    @AssertTrue(message = "title은 null 또는 공백일 수 없습니다.")
    @Schema(hidden = true)
    public boolean isTitleValid() {
        return !titlePresent || (title != null && !title.isEmpty());
    }

    @AssertTrue(message = "startsAt은 null일 수 없습니다.")
    @Schema(hidden = true)
    public boolean isStartsAtValid() {
        return !startsAtPresent || startsAt != null;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("지원하지 않는 필드: " + name);
    }

    private static String textOrNull(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("문자열 또는 null이어야 합니다.");
        }
        return value.stringValue();
    }

    public String title() { return title; }
    public OffsetDateTime startsAt() { return startsAt; }
    public String location() { return location; }
    public Integer version() { return version; }
    public boolean titlePresent() { return titlePresent; }
    public boolean startsAtPresent() { return startsAtPresent; }
    public boolean locationPresent() { return locationPresent; }
}
