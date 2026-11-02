package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record SessionDetailResDTO(
		UUID sessionId,
		String title,
		LocalDate sessionDate,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		String location,
		SessionStatus status
) {

	public static SessionDetailResDTO from(Session session) {
		return new SessionDetailResDTO(
				session.getId(),
				session.getTitle(),
				session.getSessionDate(),
				session.getStartTime(),
				session.getEndTime(),
				session.getLocation(),
				session.getStatus()
		);
	}
}
