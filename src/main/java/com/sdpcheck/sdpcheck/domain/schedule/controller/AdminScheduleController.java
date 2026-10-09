package com.sdpcheck.sdpcheck.domain.schedule.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleListResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/schedules")
public class AdminScheduleController {
    private final ScheduleService service;

    public AdminScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody CreateScheduleRequest request) {
        requireAdmin(member);
        ScheduleResponse result = service.create(request, member.memberId());
        return ResponseEntity.created(URI.create("/api/v1/admin/schedules/" + result.id()))
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "SCHEDULE_CREATED", "일정이 생성되었습니다.", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ScheduleListResponse>> list(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(member);
        ScheduleListResponse result = service.list(from, to, type, page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "SCHEDULE_LIST_OK", "일정 목록을 조회했습니다.", result));
    }

    private static void requireAdmin(AuthenticatedMember member) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
    }
}
