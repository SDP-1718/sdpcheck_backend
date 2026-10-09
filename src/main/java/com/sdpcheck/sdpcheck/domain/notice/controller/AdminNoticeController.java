package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeListResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/admin/notices")
public class AdminNoticeController {
    private final NoticeService service;

    public AdminNoticeController(NoticeService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NoticeResponse>> create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody CreateNoticeRequest request) {
        requireAdmin(member);
        NoticeResponse result = service.create(request, member.memberId());
        return ResponseEntity.created(URI.create("/api/v1/admin/notices/" + result.id()))
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTICE_CREATED", "공지가 생성되었습니다.", result));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NoticeListResponse>> list(
            @AuthenticationPrincipal AuthenticatedMember member) {
        requireAdmin(member);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTICE_LIST_OK", "공지 목록을 조회했습니다.", service.list()));
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
