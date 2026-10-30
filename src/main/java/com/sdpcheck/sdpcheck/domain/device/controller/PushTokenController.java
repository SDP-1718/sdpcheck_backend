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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/devices/push-tokens")
@Tag(name = "MYPAGE")
@SecurityRequirement(name = "bearerAuth")
public class PushTokenController {
    private final PushTokenService pushTokenService;

    @Operation(summary = "푸시 토큰 등록", description = "로그인한 회원의 기기 푸시 토큰을 등록하고 tokenId를 반환합니다. 토큰은 공백 없이 최대 512자입니다. 기존 토큰은 현재 회원 소유로 갱신하며 성공 시 200을 반환합니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<PushTokenResponse>> addPushToken(
            @Valid @RequestBody PushTokenRequest pushTokenRequest,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember){
        PushTokenResponse pushTokenResponse = pushTokenService.addPushToken(
                authenticatedMember.memberId(),
                pushTokenRequest
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "PUSH_TOKEN_REGISTER_SUCCESS",
                        "푸시 토큰 등록에 성공했습니다.", pushTokenResponse));
    }

    @Operation(summary = "푸시 토큰 삭제", description = "로그인한 회원이 소유한 푸시 토큰을 삭제합니다. tokenId는 양수이며 없는 토큰·다른 회원의 토큰·이미 삭제된 토큰은 404입니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "푸시 토큰 삭제 완료 (본문 없음)", content = @Content))
    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> deletePushToken(
            @PathVariable @Positive Long tokenId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
        pushTokenService.deletePushToken(authenticatedMember.memberId(), tokenId);

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
