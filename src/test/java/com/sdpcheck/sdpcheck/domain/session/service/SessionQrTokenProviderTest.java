package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.CheckInErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionQrTokenProviderTest {
    private static final String SECRET = "test-only-secret-key-for-ci-0123456789-abcdefghij";
    private static final String OTHER_SECRET = "different-test-secret-key-for-ci-0123456789-abcdefghij";
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    private final SessionQrTokenProvider provider = new SessionQrTokenProvider(SECRET);

    @Test
    void validatesATokenCreatedForTheSameSessionBeforeItsExpiry() {
        UUID sessionId = UUID.randomUUID();
        String token = provider.createToken(sessionId,
                OffsetDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));

        assertDoesNotThrow(() -> provider.validateToken(token, sessionId, NOW));
    }

    @Test
    void acceptsTheLastInstantBeforeExpiry() {
        UUID sessionId = UUID.randomUUID();
        Instant expiration = NOW.plusSeconds(60);
        String token = token(SECRET, sessionId.toString(), "SESSION_QR", expiration);

        assertDoesNotThrow(() -> provider.validateToken(token, sessionId, expiration.minusNanos(1)));
    }

    @Test
    void treatsTheExactExpiryInstantAsExpiredWithoutAnyGracePeriod() {
        UUID sessionId = UUID.randomUUID();
        String token = token(SECRET, sessionId.toString(), "SESSION_QR", NOW);

        assertError(CheckInErrorCode.QR_TOKEN_EXPIRED,
                () -> provider.validateToken(token, sessionId, NOW));
    }

    @Test
    void mapsAnAlreadyExpiredValidQrTokenToTheExpiredError() {
        UUID sessionId = UUID.randomUUID();
        String token = token(SECRET, sessionId.toString(), "SESSION_QR", NOW.minusMillis(1));

        assertError(CheckInErrorCode.QR_TOKEN_EXPIRED,
                () -> provider.validateToken(token, sessionId, NOW));
    }

    @Test
    void rejectsAValidQrTokenForAnotherSession() {
        UUID encodedSessionId = UUID.randomUUID();
        String token = token(SECRET, encodedSessionId.toString(), "SESSION_QR", NOW.plusSeconds(60));

        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(token, UUID.randomUUID(), NOW));
    }

    @Test
    void rejectsTokensWithAWrongOrMissingTokenType() {
        UUID sessionId = UUID.randomUUID();
        String wrongType = token(SECRET, sessionId.toString(), "ACCESS", NOW.plusSeconds(60));
        String missingType = token(SECRET, sessionId.toString(), null, NOW.plusSeconds(60));

        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(wrongType, sessionId, NOW));
        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(missingType, sessionId, NOW));
    }

    @Test
    void rejectsTokensWithoutAnExpiryAndWithANonUuidSubject() {
        UUID sessionId = UUID.randomUUID();
        String noExpiry = token(SECRET, sessionId.toString(), "SESSION_QR", null);
        String nonUuidSubject = token(SECRET, "not-a-uuid", "SESSION_QR", NOW.plusSeconds(60));

        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(noExpiry, sessionId, NOW));
        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(nonUuidSubject, sessionId, NOW));
    }

    @Test
    void rejectsTamperedMalformedAndDifferentlySignedTokens() {
        UUID sessionId = UUID.randomUUID();
        String valid = token(SECRET, sessionId.toString(), "SESSION_QR", NOW.plusSeconds(60));
        String otherSignature = token(OTHER_SECRET, sessionId.toString(), "SESSION_QR", NOW.plusSeconds(60));
        String expired = token(SECRET, sessionId.toString(), "SESSION_QR", NOW.minusSeconds(1));

        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(tamperSignature(valid), sessionId, NOW));
        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(tamperPayload(expired), sessionId, NOW));
        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken("not-a-jwt", sessionId, NOW));
        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(otherSignature, sessionId, NOW));
    }

    @Test
    void rejectsANullExpectedSessionId() {
        UUID sessionId = UUID.randomUUID();
        String token = token(SECRET, sessionId.toString(), "SESSION_QR", NOW.plusSeconds(60));

        assertError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> provider.validateToken(token, null, NOW));
    }

    private String token(String secret, String subject, String type, Instant expiration) {
        var builder = Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(NOW.minusSeconds(60)));
        if (type != null) {
            builder.claim("token_type", type);
        }
        if (expiration != null) {
            builder.expiration(Date.from(expiration));
        }
        return builder.signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private String tamperSignature(String token) {
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    private String tamperPayload(String token) {
        int payloadStart = token.indexOf('.') + 1;
        char replacement = token.charAt(payloadStart) == 'A' ? 'B' : 'A';
        return token.substring(0, payloadStart) + replacement + token.substring(payloadStart + 1);
    }

    private void assertError(CheckInErrorCode expected, ThrowingRunnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expected, exception.getErrorCode());
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
