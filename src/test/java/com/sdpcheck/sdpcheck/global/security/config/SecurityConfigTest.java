package com.sdpcheck.sdpcheck.global.security.config;

import com.sdpcheck.sdpcheck.domain.auth.config.KakaoConfig;
import com.sdpcheck.sdpcheck.domain.auth.service.KakaoOAuthStateService;
import com.sdpcheck.sdpcheck.domain.auth.controller.AuthController;
import com.sdpcheck.sdpcheck.domain.auth.controller.KakaoAuthController;
import com.sdpcheck.sdpcheck.domain.auth.cookie.RefreshTokenCookieProvider;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.OnboardingRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.AccessTokenResponse;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.OnboardingResponse;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.LoginResponse;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.LoginResult;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.RefreshResponse;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.RefreshResult;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import jakarta.servlet.http.Cookie;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AuthController.class, KakaoAuthController.class,
        SecurityConfigTest.ProtectedController.class}, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000",
        "jwt.refresh-cookie-secure=true",
        "kakao.client-id=test-client",
        "kakao.redirect-uri=http://localhost:8080/login/oauth2/code/kakao",
        "auth.frontend-callback-url=http://localhost:5173/auth/callback"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, RefreshTokenCookieProvider.class,
        KakaoConfig.class, KakaoOAuthStateService.class, GlobalExceptionHandler.class, SecurityConfigTest.ProtectedController.class})
class SecurityConfigTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtProvider jwtProvider;
    @Autowired
    private KakaoOAuthStateService states;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private MemberRepository memberRepository;

    @Test
    void kakaoCallbackRemainsPublicAndRedirectsWithRefreshCookie() throws Exception {
        when(authService.kakaoLogin("authorization-code")).thenReturn("kakao-refresh");
        String state = states.createState();

        mockMvc.perform(get("/login/oauth2/code/kakao")
                        .param("code", "authorization-code")
                        .param("state", state)
                        .cookie(new Cookie(KakaoOAuthStateService.COOKIE_NAME, state))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:5173/auth/callback"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("refreshToken=kakao-refresh"))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("HttpOnly"))))
                .andExpect(content().string(""));

        verify(authService).kakaoLogin("authorization-code");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void loginStartIsPublicAndSetsStateCookie() throws Exception {
        mockMvc.perform(get("/api/v1/auth/kakao/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("https://kauth.kakao.com/oauth/authorize?")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("state=")))
                .andExpect(header().string(HttpHeaders.LOCATION, containsString("client_id=test-client")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("kakaoOAuthState=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=300")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/login/oauth2/code/kakao")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verifyNoInteractions(authService, memberRepository);
    }

    @Test
    void missingOrMismatchedStateCannotIssueTokens() throws Exception {
        mockMvc.perform(get("/login/oauth2/code/kakao").param("code", "code"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"));
        mockMvc.perform(get("/login/oauth2/code/kakao").param("code", "code")
                        .param("state", states.createState())
                        .cookie(new Cookie(KakaoOAuthStateService.COOKIE_NAME, states.createState())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_OAUTH_STATE"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verifyNoInteractions(authService, memberRepository);
    }

    @Test
    void cancelledLoginClearsStateWithoutCallingAuthService() throws Exception {
        String state = states.createState();
        mockMvc.perform(get("/login/oauth2/code/kakao").param("error", "access_denied")
                        .param("state", state).cookie(new Cookie(KakaoOAuthStateService.COOKIE_NAME, state)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("KAKAO_AUTHORIZATION_CODE_MISSING"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("kakaoOAuthState=;")));
        verifyNoInteractions(authService, memberRepository);
    }

    @Test
    void completedMemberReceivesHomeInformationWithAccessToken() throws Exception {
        when(authService.issueAccessToken("current-refresh"))
                .thenReturn(new AccessTokenResponse("access-token", true, "회원", 17));

        mockMvc.perform(post("/api/v1/auth/access-token")
                        .cookie(new Cookie("refreshToken", "current-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.result.name").value("회원"))
                .andExpect(jsonPath("$.result.generation").value(17))
                .andExpect(jsonPath("$.result.password").doesNotExist())
                .andExpect(jsonPath("$.result.kakaoId").doesNotExist());

        verify(authService).issueAccessToken("current-refresh");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void accessTokenAndHomeInformationRequireRefreshCookie() throws Exception {
        when(authService.issueAccessToken(isNull()))
                .thenThrow(new BusinessException(AuthErrorCode.INVALID_TOKEN));
        mockMvc.perform(post("/api/v1/auth/access-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        verify(authService).issueAccessToken(null);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void accessTokenEndpointSkipsJwtAuthentication() throws Exception {
        when(authService.issueAccessToken("current-refresh"))
                .thenReturn(new AccessTokenResponse("access-token", false, null, null));
        mockMvc.perform(post("/api/v1/auth/access-token")
                        .cookie(new Cookie("refreshToken", "current-refresh"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.result.name").value(nullValue()))
                .andExpect(jsonPath("$.result.generation").value(nullValue()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

        verify(authService).issueAccessToken("current-refresh");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void incompleteMemberCanOnboardUsingAuthenticatedMemberId() throws Exception {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(Member.createKakaoMember(123L)));
        when(authService.completeOnboarding(eq(1L), any(OnboardingRequest.class)))
                .thenReturn(new OnboardingResponse("회원", 17));

        mockMvc.perform(post("/api/v1/auth/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"회원\",\"generation\":17,\"inviteCode\":\"invite\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("회원"))
                .andExpect(jsonPath("$.result.generation").value(17));

        verify(authService).completeOnboarding(1L, new OnboardingRequest("회원", 17, "invite"));
    }

    @Test
    void onboardingRequiresAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"회원\",\"generation\":17,\"inviteCode\":\"invite\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(authService, memberRepository);
    }

    @Test
    void incompleteMemberCannotAccessGeneralOrAdminApis() throws Exception {
        when(memberRepository.findById(1L))
                .thenReturn(Optional.of(Member.createKakaoMember(123L)));
        String accessToken = jwtProvider.createAccessToken(1L);

        for (String path : new String[]{"/test/protected", "/api/v1/admin/notices"}) {
            mockMvc.perform(get(path)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
    }

    @Test
    void missingTokenIsUnauthorizedWithApiEnvelope() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void accessTokenAuthenticatesMemberAndContextIsNotReused() throws Exception {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                new Member("member", "hash", "회원", 17, Role.MEMBER)));
        mockMvc.perform(get("/test/protected")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(1))
                .andExpect(jsonPath("$.loginId").value("member"))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get("/test/protected")).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshTokenCannotAuthenticateApiRequest() throws Exception {
        assertInvalidToken("Bearer " + jwtProvider.createRefreshToken(1L));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void malformedExpiredAndForgedTokensAreUnauthorized() throws Exception {
        assertInvalidToken("Bearer broken.token");
        assertInvalidToken("Bearer ");
        assertInvalidToken("Basic credentials");
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 60_000);
        assertInvalidToken("Bearer " + expired.createAccessToken(1L));
        var forged = new JwtProvider("another-test-secret-key-0123456789-abcdefghij", 60_000, 120_000);
        assertInvalidToken("Bearer " + forged.createAccessToken(1L));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void deletedMemberCannotAuthenticate() throws Exception {
        when(memberRepository.findById(1L)).thenReturn(Optional.empty());
        assertInvalidToken("Bearer " + jwtProvider.createAccessToken(1L));
    }

    @Test
    void loginRemainsPublicAndReturnsRefreshTokenOnlyInCookie() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(LoginResult.of(
                new LoginResponse(1L, "member", "회원", 17, "access-token"), "refresh-token"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"member\",\"password\":\"password\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=refresh-token")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=120")))
                .andExpect(jsonPath("$.result.accessToken").value("access-token"))
                .andExpect(jsonPath("$.result.refreshToken").doesNotExist());
    }

    @Test
    void invalidPasswordIsRejectedBeforeServiceForSignupAndLogin() throws Exception {
        String password = "가".repeat(25);
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"member\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\":\"member\",\"password\":\"" + password
                                + "\",\"name\":\"회원\",\"generation\":17,\"inviteCode\":\"invite\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(authService);
    }

    @Test
    void healthIsPublicButOtherActuatorEndpointsAreProtected() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/private")).andExpect(status().isUnauthorized());
    }

    private void assertInvalidToken(String headerValue) throws Exception {
        mockMvc.perform(get("/test/protected").header(HttpHeaders.AUTHORIZATION, headerValue))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void refreshDoesNotRequireAccessTokenAndReturnsNewRefreshCookie() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(RefreshResult.of(
                RefreshResponse.from("new-access"), "new-refresh"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(new Cookie("refreshToken", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.accessToken").value("new-access"))
                .andExpect(jsonPath("$.result.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=new-refresh")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=120")));
        verify(authService).refresh("old-refresh");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void refreshIgnoresExpiredAccessTokenInHeader() throws Exception {
        when(authService.refresh("old-refresh")).thenReturn(RefreshResult.of(
                RefreshResponse.from("new-access"), "new-refresh"));
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 60_000);
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired.createAccessToken(1L))
                        .cookie(new Cookie("refreshToken", "old-refresh")))
                .andExpect(status().isOk());
        verify(authService).refresh("old-refresh");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void missingRefreshCookieIsUnauthorizedInsteadOfBadRequest() throws Exception {
        when(authService.refresh(isNull())).thenThrow(new BusinessException(AuthErrorCode.INVALID_TOKEN));
        mockMvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verify(authService).refresh(null);
    }

    @Test
    void logoutExpiresCookieAndReturnsEmptySuccessResult() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout").cookie(new Cookie("refreshToken", "current-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=;")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Secure")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Lax")));
        verify(authService).logout("current-refresh");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void logoutSucceedsWithoutCookieOrAccessToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(nullValue()))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(authService).logout(null);
        verifyNoInteractions(memberRepository);
    }

    @Test
    void logoutIgnoresExpiredAccessToken() throws Exception {
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 60_000);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expired.createAccessToken(1L))
                        .cookie(new Cookie("refreshToken", "expired-refresh")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
        verify(authService).logout("expired-refresh");
        verifyNoInteractions(memberRepository);
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/test/protected")
        AuthenticatedMember me(@AuthenticationPrincipal AuthenticatedMember member) {
            return member;
        }
    }
}
