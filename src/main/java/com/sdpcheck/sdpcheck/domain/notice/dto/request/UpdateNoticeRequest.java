package com.sdpcheck.sdpcheck.domain.notice.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Schema(description = "수정할 필드만 전달합니다. null은 허용하지 않으며 imageIds의 빈 배열은 모든 첨부 제거입니다.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class UpdateNoticeRequest {
    private String title;
    private String content;
    private Boolean isPinned;
    private List<UUID> imageIds;

    @JsonSetter("title")
    @Schema(implementation = String.class, minLength = 1, example = "정기 세션 장소 변경")
    public void setTitle(JsonNode value) {
        title = requiredText(value).strip();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("title은 공백일 수 없습니다.");
        }
    }

    @JsonSetter("content")
    @Schema(implementation = String.class, minLength = 1, example = "학생회관 301호로 변경되었습니다.")
    public void setContent(JsonNode value) {
        content = requiredText(value).strip();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("content는 공백일 수 없습니다.");
        }
    }

    @JsonSetter("isPinned")
    @Schema(implementation = Boolean.class, example = "true")
    public void setIsPinned(JsonNode value) {
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException("isPinned는 boolean이어야 합니다.");
        }
        isPinned = value.booleanValue();
    }

    @JsonSetter("imageIds")
    @Schema(implementation = UUID[].class, description = "전체 첨부 목록. 빈 배열은 모든 첨부 제거입니다.")
    public void setImageIds(JsonNode value) {
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException("imageIds는 배열이어야 합니다.");
        }
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < value.size(); i++) {
            String id = requiredText(value.get(i));
            UUID parsed = UUID.fromString(id);
            if (!parsed.toString().equalsIgnoreCase(id)) {
                throw new IllegalArgumentException("파일 ID는 UUID 형식이어야 합니다.");
            }
            ids.add(parsed);
        }
        if (ids.stream().distinct().count() != ids.size()) {
            throw new IllegalArgumentException("imageIds는 중복될 수 없습니다.");
        }
        imageIds = List.copyOf(ids);
    }

    @AssertTrue(message = "수정할 필드를 하나 이상 전달해야 합니다.")
    @Schema(hidden = true)
    public boolean isChangePresent() {
        return title != null || content != null || isPinned != null || imageIds != null;
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("지원하지 않는 필드: " + name);
    }

    private static String requiredText(JsonNode value) {
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("null이 아닌 문자열이어야 합니다.");
        }
        return value.stringValue();
    }

    public String title() { return title; }
    public String content() { return content; }
    public Boolean pinned() { return isPinned; }
    public List<UUID> imageIds() { return imageIds; }
}
