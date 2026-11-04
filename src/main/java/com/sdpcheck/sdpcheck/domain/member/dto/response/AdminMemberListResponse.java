package com.sdpcheck.sdpcheck.domain.member.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AdminMemberListResponse(
        @Schema(description = "회원 목록, 없으면 빈 배열") List<AdminMemberListItem> members,
        @Schema(description = "현재 페이지 (0부터 시작)", example = "0") int page,
        @Schema(description = "요청한 페이지 크기", example = "20") int size,
        @Schema(description = "다음 페이지 존재 여부", example = "false") boolean hasNext
) {
    public AdminMemberListResponse {
        members = List.copyOf(members);
    }
}
