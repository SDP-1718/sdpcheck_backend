package com.sdpcheck.sdpcheck.domain.attendance.controller;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryPageResponse;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members/me/attendances")
public class AttendanceController {
    private final AttendanceQueryService service;

    public AttendanceController(AttendanceQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<AttendanceHistoryPageResponse>> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(AttendanceQueryService.MAX_PAGE_SIZE) int size) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getMyAttendances(member.memberId(), page, size)));
    }
}
