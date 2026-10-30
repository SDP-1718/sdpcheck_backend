package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.CheckInErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SessionQrTokenProvider {

	public static final Duration TOKEN_TTL = Duration.ofSeconds(60);
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

	public void validateToken(String token, UUID sessionId, Instant now) {
		try {
			Claims claims = Jwts.parser()
					.verifyWith(secretKey)
					.clock(() -> Date.from(now))
					.build()
					.parseSignedClaims(token)
					.getPayload();
			validateClaims(claims, sessionId);
			if (!claims.getExpiration().toInstant().isAfter(now)) {
				throw new BusinessException(CheckInErrorCode.QR_TOKEN_EXPIRED);
			}
		} catch (ExpiredJwtException exception) {
			validateClaims(exception.getClaims(), sessionId);
			throw new BusinessException(CheckInErrorCode.QR_TOKEN_EXPIRED);
		} catch (JwtException | IllegalArgumentException exception) {
			throw new BusinessException(CheckInErrorCode.INVALID_QR_TOKEN);
		}
	}

	private void validateClaims(Claims claims, UUID sessionId) {
		if (!SESSION_QR_TOKEN.equals(claims.get(TOKEN_TYPE_CLAIM))
				|| sessionId == null || !sessionId.toString().equals(claims.getSubject())
				|| claims.getExpiration() == null) {
			throw new BusinessException(CheckInErrorCode.INVALID_QR_TOKEN);
		}
	}
}
