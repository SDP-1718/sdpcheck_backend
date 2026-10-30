package com.sdpcheck.sdpcheck.domain.member.dto.response;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;

public record MyInfoResponse(
        String name,
        Integer generation,
        Role role
) {

    public static MyInfoResponse from(Member member) {
        return new MyInfoResponse(
                member.getName(),
                member.getGeneration(),
                member.getRole()
        );
    }
}
