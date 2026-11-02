package com.sdpcheck.sdpcheck.domain.session.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CheckInReqDTO(
		@NotBlank @Size(max = 4096) String qrToken
) {
}
