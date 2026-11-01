package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.dto.request.MemberRoleUpdateRequest;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberGenerationsResponse;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberRoleResponse;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
    private final AdminMemberQueryService queryService;
    private final AdminMemberCommandService commandService;

    public AdminMemberController(AdminMemberQueryService queryService, AdminMemberCommandService commandService) {
        this.queryService = queryService;
        this.commandService = commandService;
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
                .body(ApiResponse.success(queryService.getMemberGenerations()));
    }

    @Operation(operationId = "updateMemberRole", summary = "회원 권한 변경",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PatchMapping("/{memberId}/role")
    public ResponseEntity<ApiResponse<MemberRoleResponse>> updateMemberRole(
            @PathVariable @Positive Long memberId,
            @Valid @RequestBody MemberRoleUpdateRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(ApiResponse.success(commandService.updateMemberRole(member.memberId(), memberId, request.role())));
    }
}
