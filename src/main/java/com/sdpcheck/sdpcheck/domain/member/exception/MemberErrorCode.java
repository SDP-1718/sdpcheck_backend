package com.sdpcheck.sdpcheck.domain.member.exception;

import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원 정보를 찾을 수 없습니다."),
    MEMBER_ONBOARDING_INCOMPLETE(HttpStatus.CONFLICT, "온보딩을 완료한 회원만 권한을 변경할 수 있습니다."),
    SELF_ROLE_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "본인의 권한은 변경할 수 없습니다."),
    LAST_ADMIN_REMOVAL_NOT_ALLOWED(HttpStatus.CONFLICT, "마지막 관리자의 권한은 해제할 수 없습니다.");

    private final HttpStatus httpStatus;
    private final String message;

    @Override
    public String getCode() {
        return name();
    }
}
