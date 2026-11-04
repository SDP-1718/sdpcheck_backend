package com.sdpcheck.sdpcheck.domain.member.dto.response;

import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminMemberListItem(
        @Schema(description = "회원 ID", example = "7") long memberId,
        @Schema(description = "회원 이름", example = "이상우") String name,
        @Schema(description = "기수", example = "18") int generation,
        @Schema(description = "현재 권한", allowableValues = {"MEMBER", "ADMIN"}) Role role
) {
}
