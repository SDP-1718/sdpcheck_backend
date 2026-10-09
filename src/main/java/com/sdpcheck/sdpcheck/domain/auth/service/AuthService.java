package com.sdpcheck.sdpcheck.domain.auth.service;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.*;
import com.sdpcheck.sdpcheck.domain.auth.entity.RefreshToken;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.repository.RefreshTokenRepository;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String MEMBER_INVITE_CODE = "SDP2026";
    private static final String ADMIN_INVITE_CODE = "SDPADMIN2026";

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public SignupResult signUp(SignupRequest signupRequest) {
        if (memberRepository.existsByLoginId(signupRequest.loginId())) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_LOGIN_ID);
        }

        String encodedPassword = passwordEncoder.encode(signupRequest.password());
        Role role = resolveRole(signupRequest.inviteCode());

        Member member = new Member(
                signupRequest.loginId(),
                encodedPassword,
                signupRequest.name(),
                signupRequest.generation(),
                role
        );

        Member savedMember = memberRepository.save(member);

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = issueRefreshToken(member.getId());

        SignupResponse signupResponse = SignupResponse.of(savedMember, accessToken);

        return SignupResult.of(signupResponse, refreshToken);
    }

    @Transactional
    public LoginResult login(LoginRequest loginRequest){
        Member member = memberRepository.findByLoginId(loginRequest.loginId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        if(!passwordEncoder.matches(loginRequest.password(), member.getPassword())){
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String refreshToken = issueRefreshToken(member.getId());

        LoginResponse loginResponse = LoginResponse.of(member, accessToken);

        return LoginResult.of(loginResponse, refreshToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    @Transactional
    public RefreshResult refresh(String refreshToken) {
        Long memberId;
        try {
            memberId = jwtProvider.getRefreshTokenMemberId(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN, exception);
        }

        RefreshToken storedToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_TOKEN));
        if (!storedToken.getToken().equals(refreshToken)
                || !storedToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String newRefreshToken = jwtProvider.createRefreshToken(member.getId());
        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()));
        storedToken.update(newRefreshToken, expiresAt);
        refreshTokenRepository.save(storedToken);

        RefreshResponse refreshResponse = RefreshResponse.from(accessToken);

        return RefreshResult.of(refreshResponse, newRefreshToken);
    }

    @Transactional(readOnly = true)
    public CheckLoginIdResponse checkLoginId(String loginId) {
        boolean exists = memberRepository.existsByLoginId(loginId);
        return new CheckLoginIdResponse(!exists);
    }

    private Role resolveRole(String inviteCode) {
        if (MEMBER_INVITE_CODE.equals(inviteCode)) {
            return Role.MEMBER;
        }

        if (ADMIN_INVITE_CODE.equals(inviteCode)) {
            return Role.ADMIN;
        }

        throw new BusinessException(AuthErrorCode.INVALID_INVITE_CODE);
    }

    private String issueRefreshToken(Long memberId) {
        String token = jwtProvider.createRefreshToken(memberId);
        LocalDateTime expiresAt = LocalDateTime.now()
                .plus(Duration.ofMillis(jwtProvider.getRefreshTokenExpiration()));

        RefreshToken entity = refreshTokenRepository.findByMemberId(memberId)
                .map(existing -> {
                    existing.update(token, expiresAt);
                    return existing;
                })
                .orElseGet(() -> new RefreshToken(memberId, token, expiresAt));

        refreshTokenRepository.save(entity);
        return token;
    }
}
