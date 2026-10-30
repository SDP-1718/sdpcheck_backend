package com.sdpcheck.sdpcheck.domain.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.OnboardingRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.*;
import com.sdpcheck.sdpcheck.domain.auth.cookie.RefreshTokenCookieProvider;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;


@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
@Tag(name = "AUTH", description = "인증 및 온보딩 API")
public class AuthController {

    private final AuthService authService;

    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    @Operation(summary = "회원가입", description = "회원 정보와 초대코드로 가입하고 로그인 토큰을 발급합니다.")
    @Hidden
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResult signupResult = authService.signUp(request);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(signupResult.refreshToken());

        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.created(signupResult.response()));
    }

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 액세스 토큰을 발급하고 HttpOnly 리프레시 토큰 쿠키를 설정합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest loginRequest){
        LoginResult loginResult = authService.login(loginRequest);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(loginResult.refreshToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(loginResult.response()));
    }

    @Operation(summary = "로그아웃", description = "쿠키의 리프레시 토큰을 폐기하고 쿠키를 삭제합니다. 액세스 토큰과 요청 본문은 필요하지 않으며, 쿠키가 없어도 204를 반환합니다. 기존 액세스 토큰은 만료 전까지 유효합니다.",
            responses = @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "204", description = "로그아웃 완료 (본문 없음)", content = @Content))
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        authService.logout(refreshToken);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.clear();

        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .build();
    }

    @Operation(summary = "온보딩 등록", description = "카카오 신규 회원이 이름·기수·초대코드를 등록합니다. 온보딩 전 액세스 토큰으로 호출하며 잘못된 초대코드는 400, 이미 완료한 회원은 409를 반환합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/onboarding")
    public ResponseEntity<ApiResponse<OnboardingResponse>> completeOnboarding(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember principal,
            @Valid @RequestBody OnboardingRequest request
            ){
        OnboardingResponse response = authService.completeOnboarding(principal.memberId(), request);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @Operation(summary = "토큰 재발급", description = "HttpOnly refreshToken 쿠키로 액세스 토큰과 새 리프레시 토큰을 발급합니다. 새 리프레시 토큰은 Set-Cookie로 전달하며 이전 토큰은 사용할 수 없습니다. 쿠키 누락·만료·불일치는 401입니다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshResponse>> refresh(
            @CookieValue(value = "refreshToken", required = false) String refreshToken) {
        RefreshResult refreshResult = authService.refresh(refreshToken);

        ResponseCookie refreshCookie = refreshTokenCookieProvider.create(refreshResult.refreshToken());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(ApiResponse.success(refreshResult.response()));
    }

    @Operation(summary = "액세스 토큰 발급", description = "카카오 로그인 후 HttpOnly refreshToken 쿠키로 액세스 토큰과 온보딩 완료 여부·이름·기수를 조회합니다. 리프레시 토큰은 갱신하지 않습니다. 쿠키 누락·만료·불일치는 401입니다.")
    @PostMapping("/access-token")
    public ResponseEntity<ApiResponse<AccessTokenResponse>> issueAccessToken(
            @CookieValue(value = "refreshToken", required = false)
            String refreshToken
    ) {
        AccessTokenResponse response =
                authService.issueAccessToken(refreshToken);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(response));
    }

    @Hidden
    @Operation(summary = "아이디 중복 확인", description = "회원가입에 사용할 로그인 아이디의 사용 가능 여부를 확인합니다.")
    @GetMapping("/check-login-id")
    public ResponseEntity<ApiResponse<CheckLoginIdResponse>> checkLoginId(@RequestParam String loginId) {
        return ResponseEntity.ok().body(ApiResponse.success(authService.checkLoginId(loginId)));
    }
}
