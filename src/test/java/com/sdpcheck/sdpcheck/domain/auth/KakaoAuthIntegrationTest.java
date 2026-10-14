package com.sdpcheck.sdpcheck.domain.auth;

import com.sdpcheck.sdpcheck.domain.auth.client.KakaoClient;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.OnboardingRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.entity.RefreshToken;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.repository.RefreshTokenRepository;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.domain.auth.service.KakaoOAuthStateService;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.config.import=",
        "auth.frontend-callback-url=http://localhost:5173/auth/callback",
        "kakao.client-id=test-client",
        "kakao.redirect-uri=http://localhost:8080/login/oauth2/code/kakao",
        "jwt.refresh-cookie-secure=false"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(KakaoAuthIntegrationTest.ProtectedController.class)
@Testcontainers
class KakaoAuthIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired private MockMvc mvc;
    @Autowired private MemberRepository members;
    @Autowired private RefreshTokenRepository tokens;
    @Autowired private AuthService auth;
    @Autowired private JwtProvider jwt;
    @Autowired private KakaoOAuthStateService states;
    @MockitoBean private KakaoClient kakao;

    @BeforeEach
    void clearMembersAndTokens() {
        tokens.deleteAll();
        members.deleteAll();
    }

    @Test
    void newKakaoMemberCanOnboardAndUseOriginalAccessToken() throws Exception {
        when(kakao.getKakaoId("new-code")).thenReturn(123L);
        callback("new-code");

        assertThat(members.count()).isEqualTo(1);
        Member member = members.findByKakaoId(123L).orElseThrow();
        assertThat(member.isOnboardingCompleted()).isFalse();
        assertThat(member.getLoginId()).isNull();
        assertThat(member.getPassword()).isNull();
        assertThat(member.getName()).isNull();
        assertThat(member.getGeneration()).isNull();
        assertThat(member.getRole()).isNull();

        RefreshToken stored = tokens.findByMemberId(member.getId()).orElseThrow();
        String refreshToken = stored.getToken();
        LocalDateTime expiresAt = stored.getExpiresAt();
        String accessBody = mvc.perform(post("/api/v1/auth/access-token")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.onboardingCompleted").value(false))
                .andExpect(jsonPath("$.result.name").value(nullValue()))
                .andExpect(jsonPath("$.result.generation").value(nullValue()))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(accessBody, "$.result.accessToken");
        assertThat(jwt.getAccessTokenMemberId(accessToken)).isEqualTo(member.getId());
        assertThat(tokens.findByMemberId(member.getId()).orElseThrow().getToken()).isEqualTo(refreshToken);
        assertThat(tokens.findByMemberId(member.getId()).orElseThrow().getExpiresAt()).isEqualTo(expiresAt);

        mvc.perform(get("/test/kakao/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/onboarding")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"회원\",\"generation\":17,\"inviteCode\":\"SDP2026\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("회원"))
                .andExpect(jsonPath("$.result.generation").value(17));
        Member completed = members.findById(member.getId()).orElseThrow();
        assertThat(completed.isOnboardingCompleted()).isTrue();
        assertThat(completed.getRole()).isEqualTo(Role.MEMBER);
        assertThat(members.count()).isEqualTo(1);
        mvc.perform(get("/test/kakao/protected").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().string(member.getId().toString()));
        mvc.perform(post("/api/v1/auth/access-token").cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.result.name").value("회원"))
                .andExpect(jsonPath("$.result.generation").value(17))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        assertThat(tokens.findByMemberId(member.getId()).orElseThrow().getToken()).isEqualTo(refreshToken);
        mvc.perform(get("/api/v1/admin/notices").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void returningKakaoMemberKeepsIdentityAndCanGetHomeInformation() throws Exception {
        Member member = Member.createKakaoMember(456L);
        member.completeOnboarding("기존회원", 18, Role.MEMBER);
        members.saveAndFlush(member);
        when(kakao.getKakaoId("first-code")).thenReturn(456L);
        when(kakao.getKakaoId("second-code")).thenReturn(456L);
        callback("first-code");
        String oldToken = tokens.findByMemberId(member.getId()).orElseThrow().getToken();
        callback("second-code");

        assertThat(members.count()).isEqualTo(1);
        assertThat(tokens.count()).isEqualTo(1);
        String refreshToken = tokens.findByMemberId(member.getId()).orElseThrow().getToken();
        assertThat(refreshToken).isNotEqualTo(oldToken);
        assertThatThrownBy(() -> auth.issueAccessToken(oldToken)).isInstanceOf(BusinessException.class);
        String accessBody = mvc.perform(post("/api/v1/auth/access-token")
                        .cookie(new Cookie("refreshToken", refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.result.name").value("기존회원"))
                .andExpect(jsonPath("$.result.generation").value(18))
                .andReturn().getResponse().getContentAsString();
        String accessToken = JsonPath.read(accessBody, "$.result.accessToken");
        assertThat(jwt.getAccessTokenMemberId(accessToken)).isEqualTo(member.getId());
        assertThatThrownBy(() -> auth.completeOnboarding(member.getId(), new OnboardingRequest("다른이름", 19, "SDPADMIN2026")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.ONBOARDING_ALREADY_COMPLETED));
        assertThat(members.findById(member.getId()).orElseThrow().getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void invalidInviteCodeRollsBackOnboardingAndAdminCodeAssignsAdmin() {
        Member member = members.saveAndFlush(Member.createKakaoMember(789L));
        assertThatThrownBy(() -> auth.completeOnboarding(member.getId(), new OnboardingRequest("회원", 17, "bad")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_INVITE_CODE));
        Member pending = members.findById(member.getId()).orElseThrow();
        assertThat(pending.isOnboardingCompleted()).isFalse();
        assertThat(pending.getName()).isNull();
        auth.completeOnboarding(member.getId(), new OnboardingRequest("관리자", 17, "SDPADMIN2026"));
        assertThat(members.findById(member.getId()).orElseThrow().getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    void callbackWithoutCodeDoesNotCreateMembersOrTokens() throws Exception {
        String state = states.createState();
        mvc.perform(get("/login/oauth2/code/kakao").param("error", "access_denied")
                        .param("state", state).cookie(new Cookie(KakaoOAuthStateService.COOKIE_NAME, state)))
                .andExpect(status().isBadRequest());
        assertThat(members.count()).isZero();
        assertThat(tokens.count()).isZero();
        verifyNoInteractions(kakao);
    }

    @Test
    void externalApiFailureReturnsErrorWithoutCreatingMembersOrTokens() throws Exception {
        when(kakao.getKakaoId("failed-code"))
                .thenThrow(new BusinessException(AuthErrorCode.KAKAO_TOKEN_REQUEST_FAILED));
        String state = states.createState();
        mvc.perform(get("/login/oauth2/code/kakao").param("code", "failed-code")
                        .param("state", state).cookie(new Cookie(KakaoOAuthStateService.COOKIE_NAME, state)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("KAKAO_TOKEN_REQUEST_FAILED"))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("kakaoOAuthState=;")));
        assertThat(members.count()).isZero();
        assertThat(tokens.count()).isZero();
    }

    @Test
    void invalidExpiredMismatchedMissingAndOrphanRefreshTokensAreRejected() throws Exception {
        Member member = members.saveAndFlush(Member.createKakaoMember(321L));
        String token = jwt.createRefreshToken(member.getId());
        tokens.save(new RefreshToken(member.getId(), token, LocalDateTime.now().plusMinutes(5)));
        mvc.perform(post("/api/v1/auth/access-token"))
                .andExpect(status().isUnauthorized());
        assertInvalidRefresh("broken.token", "INVALID_TOKEN");
        assertInvalidRefresh(jwt.createAccessToken(member.getId()), "INVALID_TOKEN");
        assertInvalidRefresh(jwt.createRefreshToken(member.getId()), "INVALID_TOKEN");
        var expiredJwt = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", 60000, -10000);
        assertInvalidRefresh(expiredJwt.createRefreshToken(member.getId()), "INVALID_TOKEN");
        RefreshToken stored = tokens.findByMemberId(member.getId()).orElseThrow();
        stored.update(token, LocalDateTime.now().minusSeconds(1));
        tokens.save(stored);
        assertInvalidRefresh(token, "INVALID_TOKEN");
        tokens.deleteAll();
        assertInvalidRefresh(token, "INVALID_TOKEN");
        String orphan = jwt.createRefreshToken(999999L);
        tokens.save(new RefreshToken(999999L, orphan, LocalDateTime.now().plusMinutes(5)));
        assertInvalidRefresh(orphan, "MEMBER_NOT_FOUND");
    }

    @Test
    void ordinarySignupRemainsCompletedAndCanReadHomeInformation() throws Exception {
        var signup = auth.signUp(new SignupRequest("ordinary", "password123!", "일반회원", 17, "SDP2026"));
        assertThat(members.findById(signup.response().memberId()).orElseThrow().isOnboardingCompleted()).isTrue();
        assertThat(auth.issueAccessToken(signup.refreshToken()).onboardingCompleted()).isTrue();
        mvc.perform(post("/api/v1/auth/access-token")
                        .cookie(new Cookie("refreshToken", signup.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.onboardingCompleted").value(true))
                .andExpect(jsonPath("$.result.name").value("일반회원"))
                .andExpect(jsonPath("$.result.generation").value(17));
    }

    private void callback(String code) throws Exception {
        var login = mvc.perform(get("/api/v1/auth/kakao/login"))
                .andExpect(status().isFound()).andReturn().getResponse();
        var query = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(login.getHeader(HttpHeaders.LOCATION)).build().getQueryParams();
        String state = query.getFirst("state");
        Cookie cookie = login.getCookie(KakaoOAuthStateService.COOKIE_NAME);
        mvc.perform(get("/login/oauth2/code/kakao").param("code", code)
                        .param("state", state).cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string(HttpHeaders.LOCATION, "http://localhost:5173/auth/callback"))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("refreshToken="))))
                .andExpect(header().stringValues(HttpHeaders.SET_COOKIE,
                        hasItem(containsString("HttpOnly"))))
                .andExpect(content().string(""));
    }

    private void assertInvalidRefresh(String token, String errorCode) throws Exception {
        mvc.perform(post("/api/v1/auth/access-token").cookie(new Cookie("refreshToken", token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(errorCode))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @RestController
    static class ProtectedController {
        @GetMapping("/test/kakao/protected")
        String memberId(@AuthenticationPrincipal AuthenticatedMember member) {
            return member.memberId().toString();
        }
    }
}
