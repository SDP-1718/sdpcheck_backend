package com.sdpcheck.sdpcheck.domain.auth;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.SignupResult;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.repository.RefreshTokenRepository;
import com.sdpcheck.sdpcheck.domain.auth.service.AuthService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AuthLogoutIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired
    private AuthService authService;
    @Autowired
    private RefreshTokenRepository tokens;
    @Autowired
    private JwtProvider jwt;

    @Test
    void logoutDeletesDatabaseTokenAndPreventsRefresh() {
        SignupResult signup = signup();
        authService.logout(signup.refreshToken());

        assertThat(tokens.findByMemberId(signup.response().memberId())).isEmpty();
        assertThatThrownBy(() -> authService.refresh(signup.refreshToken()))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_TOKEN));
        authService.logout(signup.refreshToken());
        authService.logout(null);
        authService.logout("");
        assertThat(jwt.validateAccessToken(signup.response().accessToken())).isTrue();
    }

    @Test
    void oldTokenCannotDeleteTokenFromNewLogin() {
        SignupResult signup = signup();
        var login = authService.login(new LoginRequest(signup.response().loginId(), "password123!"));
        authService.logout(signup.refreshToken());

        assertThat(tokens.findByMemberId(signup.response().memberId())).hasValueSatisfying(
                stored -> assertThat(stored.getToken()).isEqualTo(login.refreshToken()));
        authService.logout(login.refreshToken());
        assertThat(tokens.findByMemberId(signup.response().memberId())).isEmpty();
    }

    @Test
    void expiredRefreshTokenCanStillBeDeleted() {
        SignupResult signup = signup();
        Long memberId = signup.response().memberId();
        var expiredIssuer = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", 60_000, -10_000);
        String expiredToken = expiredIssuer.createRefreshToken(memberId);
        var stored = tokens.findByMemberId(memberId).orElseThrow();
        stored.update(expiredToken, LocalDateTime.now().minusSeconds(10));
        tokens.save(stored);

        assertThat(jwt.validateRefreshToken(expiredToken)).isFalse();
        authService.logout(expiredToken);
        assertThat(tokens.findByMemberId(memberId)).isEmpty();
    }

    private SignupResult signup() {
        return authService.signUp(new SignupRequest(
                "logout-" + UUID.randomUUID(), "password123!", "회원", 17, "SDP2026"));
    }
}
