package com.sdpcheck.sdpcheck.domain.auth.controller;

import com.sdpcheck.sdpcheck.domain.auth.cookie.RefreshTokenCookieProvider;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.domain.auth.config.KakaoProperties;
import com.sdpcheck.sdpcheck.domain.auth.service.KakaoOAuthStateService;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequiredArgsConstructor
@Tag(name = "AUTH")
public class KakaoAuthController {
    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    private final KakaoOAuthStateService stateService;
    private final KakaoProperties kakaoProperties;

    @Value("${auth.frontend-callback-url}")
    private String frontendCallbackUrl;

    @Operation(summary = "카카오 로그인", description = "5분짜리 HttpOnly state 쿠키를 설정하고 카카오 인증 화면으로 이동합니다. Swagger Try it out 대신 브라우저 주소창에서 이 경로를 여세요. 인증 완료 후 백엔드 콜백을 거쳐 프론트 콜백으로 이동합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "302", description = "카카오 인증 화면으로 이동 (본문 없음)", content = @Content))
    @GetMapping("/api/v1/auth/kakao/login")
    public ResponseEntity<Void> startKakaoLogin() {
        String state = stateService.createState();
        URI authorizeUri = stateService.authorizeUri(kakaoProperties, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, stateService.cookie(state).toString())
                .location(authorizeUri).build();
    }

    @Operation(summary = "카카오 로그인 콜백", description = "카카오가 전달한 인가 코드와 state 쿠키를 검증하고 회원을 식별합니다. 신규 회원은 온보딩 미완료로 생성합니다. 리프레시 토큰 쿠키 설정 후 프론트 콜백으로 이동합니다. state 오류·코드 누락은 400, 카카오 API 실패는 502입니다. 직접 호출하지 않습니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "302", description = "프론트 콜백으로 이동 (본문 없음)", content = @Content))
    @GetMapping("/login/oauth2/code/kakao")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(value = "code", required = false) String authorizationCode,
            @RequestParam(value = "state", required = false) String state,
            @CookieValue(value = KakaoOAuthStateService.COOKIE_NAME, required = false) String cookieState,
            @Parameter(hidden = true) HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, stateService.clearCookie().toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        stateService.validate(state, cookieState);
        if (!StringUtils.hasText(authorizationCode)) {
            throw new BusinessException(AuthErrorCode.KAKAO_AUTHORIZATION_CODE_MISSING);
        }
        String refreshToken = authService.kakaoLogin(authorizationCode);
        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(refreshToken);

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .location(URI.create(frontendCallbackUrl))
                .build();
    }
}
