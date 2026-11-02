package com.sdpcheck.sdpcheck.global.security.jwt;

import com.sdpcheck.sdpcheck.domain.member.enums.Role;

public record AuthenticatedMember(Long memberId, String loginId, Role role) {
}
