package com.sdpcheck.sdpcheck.domain.schedule.controller;

import com.sdpcheck.sdpcheck.domain.schedule.dto.response.SchedulePageResponse;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleQueryService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {
    private final ScheduleQueryService service;

    public ScheduleController(ScheduleQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SchedulePageResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(ScheduleQueryService.MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getSchedules(page, size)));
    }
}
