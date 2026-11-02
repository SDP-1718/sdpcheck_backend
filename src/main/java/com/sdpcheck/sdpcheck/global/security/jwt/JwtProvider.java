package com.sdpcheck.sdpcheck.global.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {
    private static final String TOKEN_TYPE_CLAIM = "token_type";
    private static final String ACCESS_TOKEN = "ACCESS";
    private static final String REFRESH_TOKEN = "REFRESH";

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration
    ){
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String createAccessToken(Long memberId){
        return createToken(memberId, accessTokenExpiration, ACCESS_TOKEN);
    }

    public String createRefreshToken(Long memberId){
        return createToken(memberId, refreshTokenExpiration, REFRESH_TOKEN);
    }

    public Long getAccessTokenMemberId(String token) {
        return getMemberId(token, ACCESS_TOKEN);
    }

    public Long getRefreshTokenMemberId(String token) {
        return getMemberId(token, REFRESH_TOKEN);
    }

    public long getRefreshTokenExpiration(){
        return refreshTokenExpiration;
    }

    public boolean validateAccessToken(String token) {
        return validateToken(token, ACCESS_TOKEN);
    }

    public boolean validateRefreshToken(String token) {
        return validateToken(token, REFRESH_TOKEN);
    }

    private boolean validateToken(String token, String tokenType) {
        try {
            getMemberId(token, tokenType);
            return true;
        } catch (JwtException | IllegalArgumentException e){
            return false;
        }
    }



    private String createToken(Long memberId, long expirationMillis, String tokenType){
        Date now = new Date();

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(memberId))
                .claim(TOKEN_TYPE_CLAIM, tokenType)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                .signWith(secretKey)
                .compact();

    }

    private Long getMemberId(String token, String tokenType) {
        Claims claims = parseClaims(token, tokenType);
        Long memberId = Long.valueOf(claims.getSubject());
        if (memberId <= 0) {
            throw new JwtException("Invalid member ID");
        }
        return memberId;
    }

    private Claims parseClaims(String token, String tokenType){
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .require(TOKEN_TYPE_CLAIM, tokenType)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (claims.getExpiration() == null) {
            throw new JwtException("Missing token expiration");
        }
        return claims;
    }
}
