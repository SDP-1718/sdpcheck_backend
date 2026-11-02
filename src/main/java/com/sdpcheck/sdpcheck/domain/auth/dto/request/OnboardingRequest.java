package com.sdpcheck.sdpcheck.domain.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record OnboardingRequest(
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
