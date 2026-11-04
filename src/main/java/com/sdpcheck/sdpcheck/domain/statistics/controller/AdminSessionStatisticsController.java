package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.service.SessionStatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistics/sessions")
public class AdminSessionStatisticsController {
    private final SessionStatisticsQueryService service;

    public AdminSessionStatisticsController(SessionStatisticsQueryService service) {
        this.service = service;
    }

    @Operation(operationId = "getSessionAttendanceStatistics", summary = "세션별 출석 추이 조회",
            description = "현재 운영 학기의 종료된 정규 세션을 예정 시작 일시·ID 오름차순으로 반환합니다. "
                    + "세션별 출석 대상 명단 전체를 집계하며, 페이지네이션 없이 전체 추이를 반환합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @GetMapping
    public ResponseEntity<ApiResponse<SessionAttendanceStatisticsResponse>> getSessionAttendanceStatistics(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.success(service.getSessionAttendanceStatistics()));
    }
}
