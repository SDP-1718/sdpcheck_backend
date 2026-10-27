package com.sdpcheck.sdpcheck.domain.auth.dto.request;

import com.sdpcheck.sdpcheck.global.validation.MaxUtf8Bytes;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "로그인 아이디는 필수입니다.")
        @Size(max = 50, message = "로그인 아이디는 최대 50자까지 입력할 수 있습니다.")
        String loginId,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상으로 입력해야 합니다.")
        @MaxUtf8Bytes(value = 72, message = "비밀번호는 UTF-8 기준 72바이트 이하로 입력해야 합니다.")
        String password,

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 50, message = "이름은 최대 50자까지 입력할 수 있습니다.")
        String name,

        @NotNull(message = "기수는 필수입니다.")
        @Positive(message = "기수는 양수여야 합니다.")
        Integer generation,

        @NotBlank(message = "초대코드는 필수입니다.")
        String inviteCode
) {
}
