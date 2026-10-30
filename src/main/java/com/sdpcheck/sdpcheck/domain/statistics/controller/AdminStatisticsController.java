package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.StatisticsSummaryResponse;
import com.sdpcheck.sdpcheck.domain.statistics.service.StatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistics")
public class AdminStatisticsController {
    private final StatisticsQueryService service;

    public AdminStatisticsController(StatisticsQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<StatisticsSummaryResponse>> getAdminStatistics(
            @AuthenticationPrincipal AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.success(service.getAdminStatistics()));
    }
}
