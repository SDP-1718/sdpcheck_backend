package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.insight.dto.response.AttendanceInsightResponse;
import com.sdpcheck.sdpcheck.domain.insight.service.AttendanceInsightService;
import com.sdpcheck.sdpcheck.domain.member.dto.request.NotificationSettingsRequest;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MyInfoResponse;
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
    private final AttendanceInsightService attendanceInsightService;

    @GetMapping
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        MyInfoResponse response = memberService.getMyInfo(authenticatedMember.memberId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "MEMBER_ME_GET_SUCCESS",
                        "내 정보 조회에 성공했습니다.", response));
    }

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

    @GetMapping("/attendance-insight")
    public ResponseEntity<ApiResponse<AttendanceInsightResponse>> getAttendanceInsight(
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
        String insight = attendanceInsightService.getAttendanceInsight(authenticatedMember.memberId());

        AttendanceInsightResponse response = new AttendanceInsightResponse(insight);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(
                        true,
                        "ATTENDANCE_INSIGHT_GET_SUCCESS",
                        "출석 인사이트 조회에 성공했습니다.",
                        response
                ));
    }


}
