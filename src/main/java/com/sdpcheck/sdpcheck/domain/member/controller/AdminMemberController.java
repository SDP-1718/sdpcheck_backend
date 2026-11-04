package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.dto.request.MemberRoleUpdateRequest;
import com.sdpcheck.sdpcheck.domain.member.dto.response.AdminMemberListResponse;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberGenerationsResponse;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberRoleResponse;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberListQueryService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/members")
public class AdminMemberController {
    private final AdminMemberQueryService queryService;
    private final AdminMemberCommandService commandService;
    private final AdminMemberListQueryService listQueryService;

    public AdminMemberController(AdminMemberQueryService queryService, AdminMemberCommandService commandService,
                                 AdminMemberListQueryService listQueryService) {
        this.queryService = queryService;
        this.commandService = commandService;
        this.listQueryService = listQueryService;
    }

    @Operation(operationId = "getAdminMembers", summary = "기수별 회원 목록 및 현재 권한 조회",
            description = "온보딩을 완료한 MEMBER·ADMIN의 이름·기수·현재 권한을 조회합니다. "
                    + "양수 기수만 포함하며, 기수 내림차순·이름 오름차순·회원 ID 오름차순으로 반환합니다. "
                    + "기수를 생략하면 전체 기수를 조회하며 운영 학기나 학기별 명단으로 제한하지 않습니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<ApiResponse<AdminMemberListResponse>> getAdminMembers(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
            @Parameter(description = "기수 필터, 생략 시 전체", schema = @Schema(minimum = "1"))
            @RequestParam(required = false) Integer generation,
            @Parameter(description = "0부터 시작하는 페이지", schema = @Schema(minimum = "0"))
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기", schema = @Schema(minimum = "1", maximum = "50"))
            @RequestParam(defaultValue = "20") int size) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .body(ApiResponse.success(listQueryService.getAdminMembers(generation, page, size)));
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
