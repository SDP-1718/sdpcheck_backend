package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.session.dto.request.CreateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.request.UpdateSessionReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CreateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.EndSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.SessionDetailResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.StartSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.UpdateSessionResDTO;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionErrorCode;
import com.sdpcheck.sdpcheck.domain.session.exception.SessionException;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionAttendanceRepository;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionRepository;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionSemesterRepository;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionSemesterRepository.CurrentSemester;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SessionService {

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
	private static final Pattern TIME_PATTERN = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

	private static final int TITLE_MAX_LENGTH = 100;
	private static final int LOCATION_MAX_LENGTH = 200;
	private static final Map<String, DayOfWeek> REPEAT_DAYS = Map.of(
			"MON", DayOfWeek.MONDAY,
			"TUE", DayOfWeek.TUESDAY,
			"WED", DayOfWeek.WEDNESDAY,
			"THU", DayOfWeek.THURSDAY,
			"FRI", DayOfWeek.FRIDAY,
			"SAT", DayOfWeek.SATURDAY,
			"SUN", DayOfWeek.SUNDAY
	);

	private final SessionRepository sessionRepository;
	private final SessionAttendanceRepository sessionAttendanceRepository;
	private final SessionSemesterRepository sessionSemesterRepository;

	public SessionService(
			SessionRepository sessionRepository,
			SessionAttendanceRepository sessionAttendanceRepository,
			SessionSemesterRepository sessionSemesterRepository
	) {
		this.sessionRepository = sessionRepository;
		this.sessionAttendanceRepository = sessionAttendanceRepository;
		this.sessionSemesterRepository = sessionSemesterRepository;
	}

	public SessionDetailResDTO getSession(UUID sessionId) {
		if (sessionId == null) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_FOUND);
		}

		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		return SessionDetailResDTO.from(session);
	}

	public SessionDetailResDTO getNextSession() {
		OffsetDateTime now = OffsetDateTime.now();
		return sessionRepository.findNextSessions(now, now.minus(Session.CHECK_IN_WINDOW), PageRequest.of(0, 1))
				.stream()
				.findFirst()
				.map(SessionDetailResDTO::from)
				.orElse(null);
	}

	@Transactional
	public CreateSessionResDTO createSession(CreateSessionReqDTO request) {
		validateRequiredFields(request);

		LocalDate sessionDate = parseDate(request.sessionDate());
		LocalTime startTime = parseTime(request.startTime());
		LocalTime endTime = isBlank(request.endTime()) ? null : parseTime(request.endTime());
		validateTimeRange(startTime, endTime);
		validateNotPast(sessionDate, startTime);

		String title = request.title().trim();
		validateLength(title, TITLE_MAX_LENGTH);
		String location = normalizeLocation(request.location());

		Optional<CurrentSemester> semester = sessionSemesterRepository.findCurrent();
		List<LocalDate> sessionDates = Boolean.TRUE.equals(request.repeatWeekly())
				? resolveWeeklyDates(sessionDate, parseRepeatDays(request.repeatDays()), semester
						.orElseThrow(() -> new SessionException(SessionErrorCode.CURRENT_SEMESTER_NOT_CONFIGURED))
						.endsOn())
				: List.of(sessionDate);
		UUID semesterId = semester.map(CurrentSemester::id).orElse(null);

		List<Session> sessions = sessionRepository.saveAll(sessionDates.stream()
				.map(date -> Session.create(title, date, startTime, endTime, location, semesterId))
				.toList());

		return CreateSessionResDTO.from(sessions.getFirst(), sessions.size());
	}

	@Transactional
	public UpdateSessionResDTO updateSession(UUID sessionId, UpdateSessionReqDTO request) {
		if (sessionId == null || request == null) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}

		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		validateUpdatable(session);

		String title = resolveText(request.title(), session.getTitle());
		LocalDate sessionDate = resolveDate(request.sessionDate(), session.getSessionDate());
		LocalTime startTime = resolveTime(request.startTime(), session.getStartTime());
		LocalTime endTime = resolveTime(request.endTime(), session.getEndTime());
		String location = request.location() == null ? session.getLocation() : normalizeLocation(request.location());
		validateTimeRange(startTime, endTime);
		validateNotPast(sessionDate, startTime);
		validateLength(title, TITLE_MAX_LENGTH);

		session.update(title, sessionDate, startTime, endTime, location);

		return UpdateSessionResDTO.from(session);
	}

	@Transactional
	public void deleteSession(UUID sessionId) {
		if (sessionId == null) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_FOUND);
		}

		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		validateDeletable(session);
		session.delete(OffsetDateTime.now());
	}

	@Transactional
	public StartSessionResDTO startSession(UUID sessionId) {
		if (sessionId == null) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_FOUND);
		}

		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		validateStartable(session);
		session.start(OffsetDateTime.now());
		sessionAttendanceRepository.registerAllMembersAsTargets(sessionId);

		return StartSessionResDTO.from(session);
	}

	@Transactional
	public EndSessionResDTO endSession(UUID sessionId) {
		if (sessionId == null) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_FOUND);
		}

		Session session = sessionRepository.findByIdAndDeletedAtIsNull(sessionId)
				.orElseThrow(() -> new SessionException(SessionErrorCode.SESSION_NOT_FOUND));

		validateEndable(session);
		session.end(OffsetDateTime.now());
		sessionAttendanceRepository.markRemainingTargetsAbsent(sessionId);

		return EndSessionResDTO.from(session);
	}

	private void validateRequiredFields(CreateSessionReqDTO request) {
		if (request == null
				|| isBlank(request.title())
				|| isBlank(request.sessionDate())
				|| isBlank(request.startTime())) {
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

	private void validateStartable(Session session) {
		if (session.getStatus() == SessionStatus.IN_PROGRESS) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_STARTED);
		}
		if (session.getStatus() != SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_ENDED);
		}
	}

	private void validateEndable(Session session) {
		if (session.getStatus() == SessionStatus.SCHEDULED) {
			throw new SessionException(SessionErrorCode.SESSION_NOT_STARTED);
		}
		if (session.getStatus() != SessionStatus.IN_PROGRESS) {
			throw new SessionException(SessionErrorCode.SESSION_ALREADY_ENDED);
		}
	}

	private boolean hasAlreadyStarted(Session session) {
		return !OffsetDateTime.now().isBefore(session.getStartsAt());
	}

	private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
		if (endTime != null && !startTime.isBefore(endTime)) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_TIME_RANGE);
		}
	}

	private void validateNotPast(LocalDate sessionDate, LocalTime startTime) {
		if (!OffsetDateTime.now().isBefore(Session.toInstant(sessionDate, startTime))) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_DATE_TIME);
		}
	}

	private void validateLength(String value, int maxLength) {
		if (value.length() > maxLength) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
	}

	private String normalizeLocation(String value) {
		if (isBlank(value)) {
			return null;
		}
		String location = value.trim();
		validateLength(location, LOCATION_MAX_LENGTH);
		return location;
	}

	private Set<DayOfWeek> parseRepeatDays(List<String> values) {
		if (values == null || values.isEmpty()) {
			throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
		}
		Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
		for (String value : values) {
			DayOfWeek day = value == null ? null : REPEAT_DAYS.get(value);
			if (day == null) {
				throw new SessionException(SessionErrorCode.INVALID_SESSION_INPUT);
			}
			days.add(day);
		}
		return days;
	}

	// The chosen date is always the first session; later dates follow the selected weekdays.
	private List<LocalDate> resolveWeeklyDates(LocalDate firstDate, Set<DayOfWeek> days, LocalDate repeatUntil) {
		List<LocalDate> dates = new ArrayList<>(List.of(firstDate));
		for (LocalDate date = firstDate.plusDays(1); !date.isAfter(repeatUntil); date = date.plusDays(1)) {
			if (days.contains(date.getDayOfWeek())) {
				dates.add(date);
			}
		}
		return dates;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
