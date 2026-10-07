package com.sdpcheck.sdpcheck.domain.session.dto.request;

public record UpdateSessionReqDTO(
		String title,
		String sessionDate,
		String startTime,
		String endTime,
		String location
) {
}
