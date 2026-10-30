package com.sdpcheck.sdpcheck.domain.device.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushTokenRequest(
        @NotBlank(message = "토큰은 필수입니다.")
        @Size(max = 512, message = "토큰은 최대 512자까지 입력할 수 있습니다.")
        @Pattern(regexp = "\\S+", message = "토큰에는 공백을 포함할 수 없습니다.")
        String token
) {
}
