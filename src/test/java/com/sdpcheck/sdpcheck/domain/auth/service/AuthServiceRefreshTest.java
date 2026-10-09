package com.sdpcheck.sdpcheck.domain.auth.service;

import com.sdpcheck.sdpcheck.domain.auth.dto.response.RefreshResult;
import com.sdpcheck.sdpcheck.domain.auth.entity.RefreshToken;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.repository.RefreshTokenRepository;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceRefreshTest {
    private static final String SECRET = "test-only-secret-key-for-ci-0123456789-abcdefghij";
    private final MemberRepository members = mock(MemberRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final JwtProvider jwt = new JwtProvider(SECRET, 60_000, 120_000);
    private final AuthService service = new AuthService(members, mock(PasswordEncoder.class), jwt, tokens);

    @Test
    void rotatesStoredTokenAndRejectsReuse() {
        String oldToken = jwt.createRefreshToken(1L);
        LocalDateTime oldExpiration = LocalDateTime.now().plusSeconds(30);
        RefreshToken stored = new RefreshToken(1L, oldToken, oldExpiration);
        when(tokens.findByMemberId(1L)).thenReturn(Optional.of(stored));
        Member member = new Member("member", "hash", "회원", 17, Role.MEMBER);
        ReflectionTestUtils.setField(member, "id", 1L);
        when(members.findById(1L)).thenReturn(Optional.of(member));

        RefreshResult result = service.refresh(oldToken);

        assertThat(result.refreshToken()).isNotEqualTo(oldToken).isEqualTo(stored.getToken());
        assertThat(stored.getExpiresAt()).isAfter(oldExpiration);
        assertThat(jwt.getAccessTokenMemberId(result.response().accessToken())).isEqualTo(1L);
        assertThat(jwt.getRefreshTokenMemberId(result.refreshToken())).isEqualTo(1L);
        assertFailure(() -> service.refresh(oldToken), AuthErrorCode.INVALID_TOKEN);
        verify(tokens).save(stored);
    }

    @Test
    void rejectsMissingDatabaseTokenWithoutRecreatingIt() {
        when(tokens.findByMemberId(1L)).thenReturn(Optional.empty());
        assertFailure(() -> service.refresh(jwt.createRefreshToken(1L)), AuthErrorCode.INVALID_TOKEN);
        verify(tokens, never()).save(any());
        verifyNoInteractions(members);
    }

    @Test
    void rejectsTokenReplacedByAnotherLogin() {
        String oldToken = jwt.createRefreshToken(1L);
        String currentToken = jwt.createRefreshToken(1L);
        RefreshToken stored = new RefreshToken(1L, currentToken, LocalDateTime.now().plusMinutes(2));
        when(tokens.findByMemberId(1L)).thenReturn(Optional.of(stored));
        assertFailure(() -> service.refresh(oldToken), AuthErrorCode.INVALID_TOKEN);
        assertThat(stored.getToken()).isEqualTo(currentToken);
        verify(tokens, never()).save(any());
        verifyNoInteractions(members);
    }

    @Test
    void rejectsExpiredDatabaseTokenEvenIfJwtHasNotExpired() {
        String token = jwt.createRefreshToken(1L);
        when(tokens.findByMemberId(1L)).thenReturn(Optional.of(
                new RefreshToken(1L, token, LocalDateTime.now().minusSeconds(1))));
        assertFailure(() -> service.refresh(token), AuthErrorCode.INVALID_TOKEN);
        verify(tokens, never()).save(any());
        verifyNoInteractions(members);
    }

    @Test
    void rejectsDeletedMemberWithoutRotatingToken() {
        String token = jwt.createRefreshToken(1L);
        RefreshToken stored = new RefreshToken(1L, token, LocalDateTime.now().plusMinutes(2));
        when(tokens.findByMemberId(1L)).thenReturn(Optional.of(stored));
        when(members.findById(1L)).thenReturn(Optional.empty());
        assertFailure(() -> service.refresh(token), AuthErrorCode.MEMBER_NOT_FOUND);
        assertThat(stored.getToken()).isEqualTo(token);
        verify(tokens, never()).save(any());
    }

    static Stream<String> invalidTokens() {
        JwtProvider jwt = new JwtProvider(SECRET, 60_000, 120_000);
        return Stream.of(null, "", "broken.token", jwt.createAccessToken(1L),
                new JwtProvider(SECRET, 60_000, -10_000).createRefreshToken(1L),
                new JwtProvider("another-test-secret-key-0123456789-abcdefghij", 60_000, 120_000)
                        .createRefreshToken(1L));
    }

    @ParameterizedTest
    @MethodSource("invalidTokens")
    void rejectsInvalidJwtBeforeDatabaseAccess(String token) {
        assertFailure(() -> service.refresh(token), AuthErrorCode.INVALID_TOKEN);
        verifyNoInteractions(tokens, members);
    }

    private void assertFailure(Runnable action, AuthErrorCode expected) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(expected));
    }
}
