package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberGenerationsResponse;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
    private final AdminMemberQueryService service;

    public AdminMemberController(AdminMemberQueryService service) {
        this.service = service;
    }

    @GetMapping("/generations")
    public ResponseEntity<ApiResponse<MemberGenerationsResponse>> getMemberGenerations(
            @AuthenticationPrincipal AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(ApiResponse.success(service.getMemberGenerations()));
    }
}
