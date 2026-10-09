package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CreateSessionResDTO(
		Long sessionId,
		String title,
		LocalDate sessionDate,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		String location,
		SessionStatus status,
		LocalDateTime createdAt
) {

	public static CreateSessionResDTO from(Session session) {
		return new CreateSessionResDTO(
				session.getId(),
				session.getTitle(),
				session.getSessionDate(),
				session.getStartTime(),
				session.getEndTime(),
				session.getLocation(),
				session.getStatus(),
				session.getCreatedAt()
		);
	}
}
