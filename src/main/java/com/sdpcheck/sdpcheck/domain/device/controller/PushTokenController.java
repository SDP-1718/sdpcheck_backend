package com.sdpcheck.sdpcheck.domain.device.controller;

import com.sdpcheck.sdpcheck.domain.device.dto.request.PushTokenRequest;
import com.sdpcheck.sdpcheck.domain.device.dto.response.PushTokenResponse;
import com.sdpcheck.sdpcheck.domain.device.service.PushTokenService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices/push-tokens")
public class PushTokenController {
    private final PushTokenService pushTokenService;

    @PostMapping
    public ResponseEntity<ApiResponse<PushTokenResponse>> addPushToken(
            @Valid @RequestBody PushTokenRequest pushTokenRequest,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
        PushTokenResponse pushTokenResponse = pushTokenService.addPushToken(
                authenticatedMember.memberId(),
                pushTokenRequest
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "PUSH_TOKEN_REGISTER_SUCCESS",
                        "푸시 토큰 등록에 성공했습니다.", pushTokenResponse));
    }

    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> deletePushToken(
            @PathVariable @Positive Long tokenId,
            @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        pushTokenService.deletePushToken(authenticatedMember.memberId(), tokenId);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
