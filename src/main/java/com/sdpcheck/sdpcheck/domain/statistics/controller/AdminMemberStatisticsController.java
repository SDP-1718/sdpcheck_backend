package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.service.MemberStatisticsQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/statistics/members")
public class AdminMemberStatisticsController {
    private final MemberStatisticsQueryService service;

    public AdminMemberStatisticsController(MemberStatisticsQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<MemberAttendanceStatisticsResponse>> getMemberAttendanceStatistics(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "ATTENDANCE_RATE_ASC") MemberStatisticsSort sort) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(ApiResponse.success(service.getMemberAttendanceStatistics(page, size, sort)));
    }
}
