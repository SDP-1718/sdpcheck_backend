package com.sdpcheck.sdpcheck.domain.auth.controller;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.*;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.refresh-cookie-secure:false}")
    private boolean refreshCookieSecure;

    private final JwtProvider jwtProvider;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResult signupResult = authService.signUp(request);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", signupResult.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()))
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.created(signupResult.response()));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest){
        LoginResult loginResult = authService.login(loginRequest);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", loginResult.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(loginResult.response()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        authService.logout(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        RefreshResult refreshResult = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshResult.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Lax")
                .path("/api/v1/auth")
                .maxAge(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()))
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(refreshResult.response()));


    }

    @GetMapping("/check-login-id")
    public ApiResponse<CheckLoginIdResponse> checkLoginId(@RequestParam String loginId) {
        return ApiResponse.success(authService.checkLoginId(loginId));
    }
}
