package com.sdpcheck.sdpcheck.domain.auth.service;

import com.sdpcheck.sdpcheck.domain.auth.dto.request.LoginRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.OnboardingRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.request.SignupRequest;
import com.sdpcheck.sdpcheck.domain.auth.dto.response.*;
import com.sdpcheck.sdpcheck.domain.auth.entity.RefreshToken;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.repository.RefreshTokenRepository;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.repository.SemesterMemberRepository;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.domain.auth.client.KakaoClient;
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
    private final KakaoClient kakaoClient;
    private final AttendanceQueryRepository attendanceQueryRepository;
    private final SemesterMemberRepository semesterMemberRepository;

    @Transactional
    public String kakaoLogin(String authorizationCode){
        Long kakaoId = kakaoClient.getKakaoId(authorizationCode);

        Member member = memberRepository.findByKakaoId(kakaoId)
                .orElseGet(()-> memberRepository.save(Member.createKakaoMember(kakaoId)));

        return issueRefreshToken(member.getId());
    }

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
    public OnboardingResponse completeOnboarding(
            Long memberId,
            OnboardingRequest request
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(()-> new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));

        if(member.isOnboardingCompleted()){
            throw new BusinessException(
                    AuthErrorCode.ONBOARDING_ALREADY_COMPLETED);
        }

        Role role = resolveRole(request.inviteCode());
        var semesterId = attendanceQueryRepository.findCurrentSemesterId()
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        member.completeOnboarding(
                request.name(),
                request.generation(),
                role
        );

        semesterMemberRepository.register(semesterId, memberId);

        return OnboardingResponse.of(request.name(), request.generation());
    }

    @Transactional
    public RefreshResult refresh(String refreshToken) {
        Member member = validateRefreshToken(refreshToken);

        String accessToken = jwtProvider.createAccessToken(member.getId());
        String newRefreshToken = issueRefreshToken(member.getId());

        return RefreshResult.of(
                RefreshResponse.from(accessToken),
                newRefreshToken
        );
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

    private Member validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        Long memberId;
        try {
            memberId = jwtProvider.getRefreshTokenMemberId(refreshToken);
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN, exception);
        }

        RefreshToken storedToken = refreshTokenRepository.findByMemberId(memberId)
                .orElseThrow(() ->
                        new BusinessException(AuthErrorCode.INVALID_TOKEN));

        if (!storedToken.getToken().equals(refreshToken)
                || !storedToken.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }

        return memberRepository.findById(memberId)
                .orElseThrow(() ->
                        new BusinessException(AuthErrorCode.MEMBER_NOT_FOUND));
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

    @Transactional(readOnly = true)
    public AccessTokenResponse issueAccessToken(String refreshToken) {
        Member member = validateRefreshToken(refreshToken);

        String accessToken = jwtProvider.createAccessToken(member.getId());

        boolean onboardingCompleted = member.isOnboardingCompleted();
        return new AccessTokenResponse(
                accessToken,
                onboardingCompleted,
                onboardingCompleted ? member.getName() : null,
                onboardingCompleted ? member.getGeneration() : null
        );
    }
}
