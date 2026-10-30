package com.sdpcheck.sdpcheck.domain.notice.controller;

import io.swagger.v3.oas.annotations.Operation;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDetailResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticePageResponse;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeQueryService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "bearerAuth")
public class NoticeController {
    private final NoticeQueryService service;

    public NoticeController(NoticeQueryService service) {
        this.service = service;
    }

    @Operation(summary = "회원 공지 목록 조회", description = "로그인한 회원이 공개된 공지 목록을 페이지 단위로 조회합니다. 삭제된 공지는 제외합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<NoticePageResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(NoticeQueryService.MAX_PAGE_SIZE) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getNotices(page, size)));
    }

    @Operation(summary = "회원 공지 상세 조회", description = "공개된 공지의 제목·본문·작성자·첨부 이미지 주소를 조회합니다.")
    @GetMapping("/{noticeId}")
    public ResponseEntity<ApiResponse<NoticeDetailResponse>> detail(@PathVariable UUID noticeId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(service.getNotice(noticeId)));
    }
}
