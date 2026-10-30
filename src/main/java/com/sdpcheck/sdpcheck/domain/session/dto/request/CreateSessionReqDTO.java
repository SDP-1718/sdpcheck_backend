package com.sdpcheck.sdpcheck.domain.session.dto.request;

import java.util.List;

public record CreateSessionReqDTO(
		String title,
		String sessionDate,
		String startTime,
		String endTime,
		String location,
		Boolean repeatWeekly,
		List<String> repeatDays
) {
}
