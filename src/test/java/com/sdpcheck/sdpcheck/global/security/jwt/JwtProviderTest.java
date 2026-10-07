package com.sdpcheck.sdpcheck.global.security.jwt;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtProviderTest {
    private static final String SECRET = "test-only-secret-key-for-ci-0123456789-abcdefghij";
    private final JwtProvider provider = new JwtProvider(SECRET, 60_000, 120_000);

    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        String access = provider.createAccessToken(1L);
        String refresh = provider.createRefreshToken(1L);

        assertThat(provider.getAccessTokenMemberId(access)).isEqualTo(1L);
        assertThat(provider.getRefreshTokenMemberId(refresh)).isEqualTo(1L);
        assertThat(provider.validateAccessToken(access)).isTrue();
        assertThat(provider.validateRefreshToken(refresh)).isTrue();
        assertThat(provider.validateAccessToken(refresh)).isFalse();
        assertThat(provider.validateRefreshToken(access)).isFalse();
        assertThatThrownBy(() -> provider.getAccessTokenMemberId(refresh)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> provider.getRefreshTokenMemberId(access)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsExpiredForgedAndMalformedTokens() {
        JwtProvider expired = new JwtProvider(SECRET, -10_000, -10_000);
        JwtProvider otherIssuer = new JwtProvider("another-test-secret-key-0123456789-abcdefghij", 60_000, 120_000);
        assertThat(provider.validateAccessToken(expired.createAccessToken(1L))).isFalse();
        assertThat(provider.validateRefreshToken(expired.createRefreshToken(1L))).isFalse();
        assertThat(provider.validateAccessToken(otherIssuer.createAccessToken(1L))).isFalse();
        assertThat(provider.validateAccessToken("broken.token")).isFalse();
        assertThat(provider.validateAccessToken(null)).isFalse();
    }

    @Test
    void rejectsLegacyTokensAndTokensWithoutExpiration() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String legacy = Jwts.builder().subject("1")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        String noExpiration = Jwts.builder().subject("1").claim("token_type", "ACCESS")
                .signWith(key).compact();
        assertThat(provider.validateAccessToken(legacy)).isFalse();
        assertThat(provider.validateRefreshToken(legacy)).isFalse();
        assertThat(provider.validateAccessToken(noExpiration)).isFalse();
    }

    @Test
    void rejectsInvalidMemberIds() {
        assertThat(provider.validateAccessToken(provider.createAccessToken(0L))).isFalse();
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String invalidSubject = Jwts.builder().subject("not-a-number").claim("token_type", "ACCESS")
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        assertThat(provider.validateAccessToken(invalidSubject)).isFalse();
    }
}
