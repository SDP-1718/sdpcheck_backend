package com.sdpcheck.sdpcheck.domain.auth.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    DUPLICATE_LOGIN_ID(
            HttpStatus.CONFLICT,
            "이미 사용 중인 로그인 아이디입니다."
    ),
    INVALID_INVITE_CODE(
            HttpStatus.BAD_REQUEST,
            "유효하지 않은 초대코드입니다."
    ),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "아이디 또는 비밀번호가 올바르지 않습니다."
    ),
    AUTHENTICATION_REQUIRED(
            HttpStatus.UNAUTHORIZED,
            "로그인이 필요합니다."
    ),
    INVALID_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "유효하지 않거나 만료된 인증 토큰입니다."
    ),
    MEMBER_NOT_FOUND(
            HttpStatus.UNAUTHORIZED,
            "인증 대상 회원을 찾을 수 없습니다."
    ),
    ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "접근 권한이 없습니다."
    ),
    INVALID_OAUTH_STATE(
            HttpStatus.BAD_REQUEST,
            "??? ??? ??? ???? ??? ???????."
    ),
    KAKAO_AUTHORIZATION_CODE_MISSING(
            HttpStatus.BAD_REQUEST,
            "??? ??? ?? ??? ????."
    ),
    KAKAO_CONFIGURATION_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "카카오 로그인 설정이 올바르지 않습니다."
    ),
    KAKAO_TOKEN_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY,
            "카카오 토큰 발급에 실패했습니다."
    ),
    KAKAO_USER_INFO_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY,
            "카카오 사용자 정보 조회에 실패했습니다."
    ),
    ONBOARDING_ALREADY_COMPLETED(
            HttpStatus.CONFLICT,
            "이미 온보딩을 완료한 회원입니다."
    );


    private final HttpStatus httpStatus;
    private final String message;

    public String getCode() {
        return name();
    }
}
