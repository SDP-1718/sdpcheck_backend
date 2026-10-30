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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/members/me")
@Tag(name = "MYPAGE", description = "내 정보 및 알림 설정 API")
@SecurityRequirement(name = "bearerAuth")
public class MemberController {
    private final MemberService memberService;
    private final AttendanceInsightService attendanceInsightService;

    @Operation(summary = "내 정보 조회", description = "온보딩을 완료한 로그인 회원의 이름·기수·권한을 조회합니다. 토큰의 회원 정보만 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        MyInfoResponse response = memberService.getMyInfo(authenticatedMember.memberId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "MEMBER_ME_GET_SUCCESS",
                        "내 정보 조회에 성공했습니다.", response));
    }

    @Operation(summary = "알림 설정 조회", description = "로그인한 회원의 세션 시작 알림과 새 공지 알림 수신 여부를 조회합니다.")
    @GetMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> getNotificationSettings(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        NotificationSettingsResponse response = memberService.getNotificationSettings(
                authenticatedMember.memberId());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTIFICATION_SETTINGS_GET_SUCCESS",
                        "알림 설정 조회에 성공했습니다.", response));
    }

    @Operation(summary = "알림 설정 수정", description = "sessionStartEnabled·newNoticeEnabled 중 전달한 값을 수정합니다. 생략하거나 null인 필드는 유지하며 하나 이상의 boolean 값이 필요합니다. 수정된 전체 설정을 반환합니다.")
    @PatchMapping("/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        NotificationSettingsResponse response = memberService.updateNotificationSettings(
                authenticatedMember.memberId(), request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "NOTIFICATION_SETTINGS_UPDATE_SUCCESS",
                        "알림 설정 수정에 성공했습니다.", response));
    }

    @Operation(summary = "출석 AI 인사이트 조회", description = "로그인한 회원의 출석 기록을 요약한 인사이트를 조회합니다. 기록이 없으면 안내 문구를, AI 비활성화·호출 실패 시 기본 출석 요약을 반환합니다.")
    @GetMapping("/attendance-insight")
    public ResponseEntity<ApiResponse<AttendanceInsightResponse>> getAttendanceInsight(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
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
