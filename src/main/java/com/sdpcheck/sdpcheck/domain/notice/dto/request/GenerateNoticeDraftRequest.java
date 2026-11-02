package com.sdpcheck.sdpcheck.domain.notice.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonSetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

@Schema(description = "AI 공지 초안 생성 요청. 메모를 바탕으로 제목과 본문을 작성합니다.",
        requiredProperties = {"memo"}, additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class GenerateNoticeDraftRequest {
    @NotBlank
    @Size(max = 2000)
    private String memo;

    public String memo() { return memo; }

    @JsonSetter("memo")
    @Schema(implementation = String.class, minLength = 1, maxLength = 2000,
            example = "10월 2일 금요일 오후 7시 정기 세션 공지", requiredMode = Schema.RequiredMode.REQUIRED)
    public void setMemo(JsonNode value) {
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException("memo는 null이 아닌 문자열이어야 합니다.");
        }
        memo = value.stringValue().strip();
    }

    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("지원하지 않는 필드: " + name);
    }
}
