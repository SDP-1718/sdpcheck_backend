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

@RestController
@RequiredArgsConstructor
public class KakaoAuthController {
    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    private final KakaoOAuthStateService stateService;
    private final KakaoProperties kakaoProperties;

    @Value("${auth.frontend-callback-url}")
    private String frontendCallbackUrl;

    @GetMapping("/api/v1/auth/kakao/login")
    public ResponseEntity<Void> startKakaoLogin() {
        String state = stateService.createState();
        URI authorizeUri = stateService.authorizeUri(kakaoProperties, state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.SET_COOKIE, stateService.cookie(state).toString())
                .location(authorizeUri).build();
    }

    @GetMapping("/login/oauth2/code/kakao")
    public ResponseEntity<Void> kakaoCallback(
            @RequestParam(value = "code", required = false) String authorizationCode,
            @RequestParam(value = "state", required = false) String state,
            @CookieValue(value = KakaoOAuthStateService.COOKIE_NAME, required = false) String cookieState,
            HttpServletResponse response) {
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
