package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.member.dto.request.NotificationSettingsRequest;
import com.sdpcheck.sdpcheck.domain.member.dto.response.NotificationSettingsResponse;
import com.sdpcheck.sdpcheck.domain.member.service.MemberService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me")
public class MemberController {
    private final MemberService memberService;

    @GetMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        NotificationSettingsResponse response = memberService.getNotificationSettings(
                authenticatedMember.memberId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTIFICATION_SETTINGS_GET_SUCCESS",
                        "알림 설정 조회에 성공했습니다.", response));
    }

    @PatchMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsRequest request,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        NotificationSettingsResponse response = memberService.updateNotificationSettings(
                authenticatedMember.memberId(), request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTIFICATION_SETTINGS_UPDATE_SUCCESS",
                        "알림 설정 수정에 성공했습니다.", response));
    }

}
