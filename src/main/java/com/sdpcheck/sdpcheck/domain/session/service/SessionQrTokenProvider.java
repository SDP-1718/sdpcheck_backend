package com.sdpcheck.sdpcheck.domain.session.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionQrTokenProvider {

	public static final Duration TOKEN_TTL = Duration.ofSeconds(60);
	// Allows a QR scanned just before expiry to finish submitting.
	private static final long SUBMIT_GRACE_SECONDS = 10;

	private static final String TOKEN_TYPE_CLAIM = "token_type";
	private static final String SESSION_QR_TOKEN = "SESSION_QR";

	private final SecretKey secretKey;

	public SessionQrTokenProvider(@Value("${jwt.secret}") String secret) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String createToken(UUID sessionId, OffsetDateTime expiresAt) {
		return Jwts.builder()
				.id(UUID.randomUUID().toString())
				.subject(sessionId.toString())
				.claim(TOKEN_TYPE_CLAIM, SESSION_QR_TOKEN)
				.issuedAt(new Date())
				.expiration(Date.from(expiresAt.toInstant()))
				.signWith(secretKey)
				.compact();
	}

	public Optional<UUID> getSessionId(String token) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(secretKey)
					.require(TOKEN_TYPE_CLAIM, SESSION_QR_TOKEN)
					.clockSkewSeconds(SUBMIT_GRACE_SECONDS)
					.build()
					.parseSignedClaims(token)
					.getPayload();
			if (claims.getExpiration() == null) {
				return Optional.empty();
			}
			return Optional.of(UUID.fromString(claims.getSubject()));
		} catch (JwtException | IllegalArgumentException exception) {
			return Optional.empty();
		}
	}
}
