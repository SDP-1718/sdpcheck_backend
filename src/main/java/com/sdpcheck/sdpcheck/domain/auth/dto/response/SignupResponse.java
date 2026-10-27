package com.sdpcheck.sdpcheck.domain.auth.dto.response;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;

public record SignupResponse(
        Long memberId,
        String loginId,
        String accessToken
) {

    public static SignupResponse of(Member member, String accessToken) {
        return new SignupResponse(
                member.getId(),
                member.getLoginId(),
                accessToken
        );
    }
}
