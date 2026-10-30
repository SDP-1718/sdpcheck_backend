package com.sdpcheck.sdpcheck.domain.attendance.controller;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceSummaryResponse;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
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
@RequestMapping("/api/v1/members/me/attendance-summary")
public class AttendanceSummaryController {
    private final AttendanceQueryService service;

    public AttendanceSummaryController(AttendanceQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AttendanceSummaryResponse>> getMyAttendanceSummary(
            @AuthenticationPrincipal AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getMyAttendanceSummary(member.memberId())));
    }
}
