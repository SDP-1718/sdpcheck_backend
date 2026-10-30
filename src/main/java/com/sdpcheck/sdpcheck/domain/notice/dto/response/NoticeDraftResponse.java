package com.sdpcheck.sdpcheck.domain.notice.dto.response;

import com.sdpcheck.sdpcheck.domain.notice.enums.NoticeDraftMode;
import io.swagger.v3.oas.annotations.media.Schema;

public record NoticeDraftResponse(
        @Schema(minLength = 1, maxLength = 100, example = "정기 세션 안내") String title,
        @Schema(minLength = 1, maxLength = 10000,
                example = "10월 2일 금요일 오후 7시에 정기 세션이 진행됩니다.") String content,
        @Schema(description = "현재는 MOCK 고정", example = "MOCK") NoticeDraftMode mode
) {
}
