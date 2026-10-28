package com.sdpcheck.sdpcheck.domain.session.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateSessionResDTO(
		UUID sessionId,
		String title,
		LocalDate sessionDate,
		@JsonFormat(pattern = "HH:mm") LocalTime startTime,
		@JsonFormat(pattern = "HH:mm") LocalTime endTime,
		String location,
		SessionStatus status,
		OffsetDateTime createdAt,
		int createdCount
) {

	public static CreateSessionResDTO from(Session session, int createdCount) {
		return new CreateSessionResDTO(
				session.getId(),
				session.getTitle(),
				session.getSessionDate(),
				session.getStartTime(),
				session.getEndTime(),
				session.getLocation(),
				session.getStatus(),
				Session.inZone(session.getCreatedAt()),
				createdCount
		);
	}
}
