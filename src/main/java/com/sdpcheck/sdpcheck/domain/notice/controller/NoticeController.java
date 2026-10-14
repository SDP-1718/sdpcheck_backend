package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDetailResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticePageResponse;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeQueryService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {
    private final NoticeQueryService service;

    public NoticeController(NoticeQueryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NoticePageResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(NoticeQueryService.MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getNotices(page, size)));
    }

    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeDetailResponse>> detail(@PathVariable UUID noticeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getNotice(noticeId)));
    }
}
