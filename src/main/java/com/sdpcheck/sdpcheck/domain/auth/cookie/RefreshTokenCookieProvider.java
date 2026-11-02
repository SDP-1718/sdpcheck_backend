package com.sdpcheck.sdpcheck.domain.auth.cookie;

import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RefreshTokenCookieProvider {

    private final JwtProvider jwtProvider;
    private final boolean secure;
    private final String sameSite;

    public RefreshTokenCookieProvider(
            JwtProvider jwtProvider,
            @Value("${jwt.refresh-cookie-secure:false}") boolean secure,
            @Value("${jwt.refresh-cookie-same-site:Lax}") String sameSite
    ) {
        this.jwtProvider = jwtProvider;
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie create(String refreshToken) {
        return build(refreshToken,
                Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from("refreshToken", value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/api/v1/auth")
                .maxAge(maxAge)
                .build();
    }
}
