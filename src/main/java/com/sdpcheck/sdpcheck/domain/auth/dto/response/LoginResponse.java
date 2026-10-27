package com.sdpcheck.sdpcheck.domain.auth.dto.response;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginResponse(
        Long memberId,
        String loginId,
        String name,
        Integer generation,
        String accessToken
) {

    public static LoginResponse of(Member member, String accessToken){
        return new LoginResponse(
                member.getId(),
                member.getLoginId(),
                member.getName(),
                member.getGeneration(),
                accessToken
        );
    }
}
