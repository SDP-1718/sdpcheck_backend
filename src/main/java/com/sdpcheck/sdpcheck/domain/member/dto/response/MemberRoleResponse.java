package com.sdpcheck.sdpcheck.domain.member.dto.response;

import com.sdpcheck.sdpcheck.domain.member.enums.Role;

public record MemberRoleResponse(
        Long memberId,
        String name,
        Integer generation,
        Role role
) {
}
