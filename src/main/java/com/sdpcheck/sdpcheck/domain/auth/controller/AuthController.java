package com.sdpcheck.sdpcheck.domain.auth.controller;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.OnboardingRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.*;
import com.sdpcheck.sdpcheck.domain.auth.cookie.RefreshTokenCookieProvider;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Hidden
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResult signupResult = authService.signUp(request);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(signupResult.refreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.created(signupResult.response()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest){
        LoginResult loginResult = authService.login(loginRequest);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(loginResult.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(loginResult.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        authService.logout(refreshToken);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.clear();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(null));
    }

    @Hidden
    @PostMapping("/onboarding")
    public ApiResponse<OnboardingResponse> completeOnboarding(
            @AuthenticationPrincipal AuthenticatedMember principal,
            @Valid @RequestBody OnboardingRequest request
            ){
        OnboardingResponse response = authService.completeOnboarding(principal.memberId(), request);

        return ApiResponse.success(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        RefreshResult refreshResult = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(refreshResult.refreshToken());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(refreshResult.response()));
    }

    @PostMapping("/access-token")
    public ApiResponse<AccessTokenResponse> issueAccessToken(
            @CookieValue(value = "refreshToken", required = false)
            String refreshToken
    ) {
        AccessTokenResponse response =
                authService.issueAccessToken(refreshToken);

        return ApiResponse.success(response);
    }

    @Hidden
    @GetMapping("/check-login-id")
    public ApiResponse<CheckLoginIdResponse> checkLoginId(@RequestParam String loginId) {
        return ApiResponse.success(authService.checkLoginId(loginId));
    }
}
