package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.session.dto.request.CreateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.request.UpdateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CreateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.UpdateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionErrorCode;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionException;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionRepository;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
	private static final Pattern TIME_PATTERN = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

	private final SessionRepository sessionRepository;

	public SessionService(SessionRepository sessionRepository) {
		this.sessionRepository = sessionRepository;
	}

	@Transactional
	public CreateSessionResDTO createSession(CreateSessionReqDTO request) {
		validateRequiredFields(request);

		LocalDate sessionDate = parseDate(request.sessionDate());
		LocalTime startTime = parseTime(request.startTime());
		LocalTime endTime = parseTime(request.endTime());
		validateTimeRange(startTime, endTime);

		Session session = Session.create(
				request.title().trim(),
				sessionDate,
				startTime,
				endTime,
				request.location().trim()
		);

		return CreateSessionResDTO.from(sessionRepository.save(session));
	}

	@Transactional
	public UpdateSessionResDTO updateSession(Long sessionId, UpdateSessionReqDTO request) {
		if (sessionId == null || request == null) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}

		Session session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		validateUpdatable(session);

		String title = resolveText(request.title(), session.getTitle());
		LocalDate sessionDate = resolveDate(request.sessionDate(), session.getSessionDate());
		LocalTime startTime = resolveTime(request.startTime(), session.getStartTime());
		LocalTime endTime = resolveTime(request.endTime(), session.getEndTime());
		String location = resolveText(request.location(), session.getLocation());
		validateTimeRange(startTime, endTime);

		session.update(title, sessionDate, startTime, endTime, location);

		return UpdateSessionResDTO.from(session);
	}

	@Transactional
	public void deleteSession(Long sessionId) {
		if (sessionId == null) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_FOUND);
		}

		Session session = sessionRepository.findById(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		validateDeletable(session);
		sessionRepository.delete(session);
	}

	private void validateRequiredFields(CreateSessionReqDTO request) {
		if (request == null
				|| isBlank(request.title())
				|| isBlank(request.sessionDate())
				|| isBlank(request.startTime())
				|| isBlank(request.endTime())
				|| isBlank(request.location())) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
	}

	private LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value, DATE_FORMATTER);
		} catch (DateTimeParseException exception) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_DATE_TIME);
		}
	}

	private LocalTime parseTime(String value) {
		if (!TIME_PATTERN.matcher(value).matches()) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_DATE_TIME);
		}

		try {
			return LocalTime.parse(value, TIME_FORMATTER);
		} catch (DateTimeException exception) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_DATE_TIME);
		}
	}

	private String resolveText(String value, String currentValue) {
		if (value == null) {
			return currentValue;
		}
		if (value.isBlank()) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
		return value.trim();
	}

	private LocalDate resolveDate(String value, LocalDate currentValue) {
		if (value == null) {
			return currentValue;
		}
		if (value.isBlank()) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
		return parseDate(value);
	}

	private LocalTime resolveTime(String value, LocalTime currentValue) {
		if (value == null) {
			return currentValue;
		}
		if (value.isBlank()) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
		return parseTime(value);
	}

	private void validateUpdatable(Session session) {
		if (session.getStatus() == SessionStatus.IN_PROGRESS) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_STARTED);
		}
		if (session.getStatus() != SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_ENDED);
		}
		if (hasAlreadyStarted(session)) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_STARTED);
		}
	}

	private void validateDeletable(Session session) {
		if (session.getStatus() == SessionStatus.IN_PROGRESS) {
			throw new SessionException(SessionErrorCode.SESSION_DELETE_ALREADY_STARTED);
		}
		if (session.getStatus() != SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_DELETE_ALREADY_ENDED);
		}
		if (hasAlreadyStarted(session)) {
			throw new SessionException(SessionErrorCode.SESSION_DELETE_ALREADY_STARTED);
		}
	}

	private boolean hasAlreadyStarted(Session session) {
		LocalDateTime sessionStartAt = LocalDateTime.of(session.getSessionDate(), session.getStartTime());
		return !LocalDateTime.now().isBefore(sessionStartAt);
	}

	private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
		if (!startTime.isBefore(endTime)) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_TIME_RANGE);
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
