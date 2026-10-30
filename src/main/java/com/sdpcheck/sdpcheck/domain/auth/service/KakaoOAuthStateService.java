package com.sdpcheck.sdpcheck.domain.auth.service;

import com.sdpcheck.sdpcheck.domain.auth.config.KakaoProperties;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.SecretKey;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.UUID;

@Service
public class KakaoOAuthStateService {
    public static final String COOKIE_NAME = "kakaoOAuthState";
    private static final Duration LIFETIME = Duration.ofMinutes(5);
    private final SecretKey key;
    private final boolean secure;
    private final Clock clock;

    @Autowired
    public KakaoOAuthStateService(@Value("${jwt.secret}") String secret,
                                  @Value("${jwt.refresh-cookie-secure:false}") boolean secure) {
        this(secret, secure, Clock.systemUTC());
    }

    KakaoOAuthStateService(String secret, boolean secure, Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.secure = secure;
        this.clock = clock;
    }

    public String createState() {
        var now = clock.instant();
        return Jwts.builder().id(UUID.randomUUID().toString())
                .claim("token_type", "KAKAO_STATE")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plus(LIFETIME)))
                .signWith(key).compact();
    }

    public void validate(String state, String cookieState) {
        if (!StringUtils.hasText(state) || !StringUtils.hasText(cookieState)
                || !MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),
                                          cookieState.getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(AuthErrorCode.INVALID_OAUTH_STATE);
        }
        try {
            var claims = Jwts.parser().verifyWith(key).require("token_type", "KAKAO_STATE")
                    .clock(() -> Date.from(clock.instant())).build().parseSignedClaims(state).getPayload();
            if (claims.getExpiration() == null
                    || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
                throw new BusinessException(AuthErrorCode.INVALID_OAUTH_STATE);
            }
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_OAUTH_STATE);
        }
    }

    public URI authorizeUri(KakaoProperties properties, String state) {
        if (!StringUtils.hasText(properties.clientId()) || !StringUtils.hasText(properties.redirectUri())) {
            throw new BusinessException(AuthErrorCode.KAKAO_CONFIGURATION_ERROR);
        }
        return UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code").queryParam("client_id", "{clientId}")
                .queryParam("redirect_uri", "{redirectUri}").queryParam("state", "{state}")
                .encode().buildAndExpand(properties.clientId(), properties.redirectUri(), state).toUri();
    }

    public ResponseCookie cookie(String state) {
        return buildCookie(state, LIFETIME);
    }

    public ResponseCookie clearCookie() {
        return buildCookie("", Duration.ZERO);
    }

    private ResponseCookie buildCookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secure)
                .sameSite("Lax").path("/login/oauth2/code/kakao").maxAge(maxAge).build();
    }
}
