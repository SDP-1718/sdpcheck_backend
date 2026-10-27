package com.sdpcheck.sdpcheck.domain.schedule.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.request.UpdateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleListResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/schedules")
@SecurityRequirement(name = "bearerAuth")
public class AdminScheduleController {
    private final ScheduleService service;

    public AdminScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ScheduleResponse>> create(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody CreateScheduleRequest request) {
        requireAdmin(member);
        ScheduleResponse result = service.create(request, member.memberId());
        return ResponseEntity.created(URI.create("/api/v1/admin/schedules/" + result.id()))
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "SCHEDULE_CREATED", "일정이 생성되었습니다.", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<ScheduleListResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
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

    @PatchMapping("/{scheduleId}")
    @Operation(operationId = "updateSchedule", summary = "일반 일정 수정")
    public ResponseEntity<ApiResponse<ScheduleResponse>> update(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdateScheduleRequest request) {
        requireAdmin(member);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "SCHEDULE_UPDATED", "일정이 수정되었습니다.",
                        service.update(scheduleId, request)));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(operationId = "deleteSchedule", summary = "일반 일정 삭제",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "일정 삭제 완료", content = @io.swagger.v3.oas.annotations.media.Content))
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable UUID scheduleId,
            @RequestParam @Min(1) int version) {
        requireAdmin(member);
        service.delete(scheduleId, version);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
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
