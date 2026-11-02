package com.sdpcheck.sdpcheck.domain.session.service;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.attendance.exception.CheckInErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.entity.Session;
import com.sdpcheck.sdpcheck.domain.session.enums.SessionStatus;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionAttendanceRepository;
import com.sdpcheck.sdpcheck.domain.session.repository.SessionRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SessionCheckInServiceTest {
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long MEMBER_ID = 1L;
    private static final Instant NOW = Instant.parse("2026-10-29T00:00:00.123456789Z");
    private static final OffsetDateTime CHECKED_IN_AT = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC)
            .truncatedTo(ChronoUnit.MICROS);

    private final SessionRepository sessionRepository = mock(SessionRepository.class);
    private final SessionAttendanceRepository attendanceRepository = mock(SessionAttendanceRepository.class);
    private final SessionQrTokenProvider qrTokenProvider = mock(SessionQrTokenProvider.class);
    private final SessionCheckInService service = new SessionCheckInService(sessionRepository, attendanceRepository,
            qrTokenProvider, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void checksInAsPresentAtTheExactFiveMinuteBoundaryWithOneMicrosecondPrecisionTimestamp() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(5));
        stubSuccessfulInsert(session, AttendanceStatus.PRESENT, 41L);

        CheckInResDTO response = service.checkIn(SESSION_ID, MEMBER_ID, request());

        assertEquals(41L, response.checkInId());
        assertEquals(SESSION_ID, response.sessionId());
        assertEquals(AttendanceStatus.PRESENT, response.attendanceStatus());
        assertEquals(OffsetDateTime.parse("2026-10-29T09:00:00.123456+09:00"), response.checkedInAt());
        verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        verify(attendanceRepository).isTargetForUpdate(SESSION_ID, MEMBER_ID);
        verify(qrTokenProvider).validateToken("qr-token", SESSION_ID, CHECKED_IN_AT.toInstant());
        verify(attendanceRepository).findAttendanceStatus(SESSION_ID, MEMBER_ID);
        verify(attendanceRepository).insertAttendance(SESSION_ID, MEMBER_ID, AttendanceStatus.PRESENT, CHECKED_IN_AT);
        verifyNoMoreInteractions(sessionRepository, attendanceRepository, qrTokenProvider);
    }

    @Test
    void checksInAsLateImmediatelyAfterTheFiveMinuteBoundary() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(5).minus(1, ChronoUnit.MICROS));
        stubSuccessfulInsert(session, AttendanceStatus.LATE, 42L);

        CheckInResDTO response = service.checkIn(SESSION_ID, MEMBER_ID, request());

        assertEquals(AttendanceStatus.LATE, response.attendanceStatus());
        verify(attendanceRepository).insertAttendance(SESSION_ID, MEMBER_ID, AttendanceStatus.LATE, CHECKED_IN_AT);
    }

    @Test
    void usesActualStartRatherThanThePlannedStartTime() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(4));
        when(session.getStartsAt()).thenReturn(CHECKED_IN_AT.plusHours(3));
        stubSuccessfulInsert(session, AttendanceStatus.PRESENT, 43L);

        CheckInResDTO response = service.checkIn(SESSION_ID, MEMBER_ID, request());

        assertEquals(AttendanceStatus.PRESENT, response.attendanceStatus());
        verify(session, never()).getStartsAt();
    }

    @Test
    void allowsLateCheckInTwentyMinutesAfterTheActualStartWithoutAnArtificialCutoff() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(20));
        stubSuccessfulInsert(session, AttendanceStatus.LATE, 44L);

        CheckInResDTO response = service.checkIn(SESSION_ID, MEMBER_ID, request());

        assertEquals(AttendanceStatus.LATE, response.attendanceStatus());
    }

    @Test
    void capturesTheClockOnceAfterAcquiringTheSessionLockAndTruncatesToDatabaseMicroseconds() {
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        SessionCheckInService clockedService = new SessionCheckInService(sessionRepository, attendanceRepository,
                qrTokenProvider, clock);
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(4));
        stubSuccessfulInsert(session, AttendanceStatus.PRESENT, 45L);

        clockedService.checkIn(SESSION_ID, MEMBER_ID, request());

        var order = inOrder(sessionRepository, clock, attendanceRepository, qrTokenProvider);
        order.verify(sessionRepository).findByIdForUpdate(SESSION_ID);
        order.verify(attendanceRepository).isTargetForUpdate(SESSION_ID, MEMBER_ID);
        order.verify(clock).instant();
        verify(clock).instant();
        verify(qrTokenProvider).validateToken("qr-token", SESSION_ID, CHECKED_IN_AT.toInstant());
        verify(attendanceRepository).insertAttendance(SESSION_ID, MEMBER_ID, AttendanceStatus.PRESENT, CHECKED_IN_AT);
    }

    @Test
    void anExistingAttendanceIsRejectedWithoutAnInsert() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(1));
        stubSessionAndTarget(session);
        when(attendanceRepository.findAttendanceStatus(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(AttendanceStatus.PRESENT));

        assertCheckInError(CheckInErrorCode.ALREADY_CHECKED_IN,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verify(attendanceRepository, never()).insertAttendance(eq(SESSION_ID), eq(MEMBER_ID),
                eq(AttendanceStatus.PRESENT), eq(CHECKED_IN_AT));
    }

    @Test
    void aConcurrentDuplicateDetectedByTheAtomicInsertIsRejected() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(1));
        stubSessionAndTarget(session);
        when(attendanceRepository.findAttendanceStatus(SESSION_ID, MEMBER_ID)).thenReturn(Optional.empty());
        when(attendanceRepository.insertAttendance(SESSION_ID, MEMBER_ID, AttendanceStatus.PRESENT, CHECKED_IN_AT))
                .thenReturn(Optional.empty());

        assertCheckInError(CheckInErrorCode.ALREADY_CHECKED_IN,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verify(attendanceRepository).insertAttendance(SESSION_ID, MEMBER_ID, AttendanceStatus.PRESENT, CHECKED_IN_AT);
    }

    @Test
    void anInvalidQrStopsBeforeExistingAttendanceOrInsertChecks() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(1));
        stubSessionAndTarget(session);
        doThrow(new BusinessException(CheckInErrorCode.INVALID_QR_TOKEN)).when(qrTokenProvider)
                .validateToken("qr-token", SESSION_ID, CHECKED_IN_AT.toInstant());

        assertCheckInError(CheckInErrorCode.INVALID_QR_TOKEN,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verify(attendanceRepository, never()).findAttendanceStatus(SESSION_ID, MEMBER_ID);
        verify(attendanceRepository, never()).insertAttendance(eq(SESSION_ID), eq(MEMBER_ID),
                eq(AttendanceStatus.PRESENT), eq(CHECKED_IN_AT));
    }

    @Test
    void aNonTargetMemberIsDeniedBeforeQrValidationOrInsert() {
        Session session = inProgressSession(CHECKED_IN_AT.minusMinutes(1));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(attendanceRepository.isTargetForUpdate(SESSION_ID, MEMBER_ID)).thenReturn(false);

        assertCheckInError(AuthErrorCode.ACCESS_DENIED,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verifyNoInteractions(qrTokenProvider);
        verify(attendanceRepository, never()).findAttendanceStatus(SESSION_ID, MEMBER_ID);
        verify(attendanceRepository, never()).insertAttendance(eq(SESSION_ID), eq(MEMBER_ID),
                eq(AttendanceStatus.PRESENT), eq(CHECKED_IN_AT));
    }

    @Test
    void aMissingOrDeletedSessionIsNotExposedAsAnActiveCheckInTarget() {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.empty());

        assertCheckInError(CheckInErrorCode.SESSION_NOT_FOUND,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));
        verifyNoInteractions(attendanceRepository, qrTokenProvider);
    }

    @Test
    void aDeletedSessionIsNotFoundBeforeAnyTargetOrQrAccess() {
        Session deleted = mock(Session.class);
        when(deleted.getDeletedAt()).thenReturn(CHECKED_IN_AT.minusSeconds(1));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(deleted));

        assertCheckInError(CheckInErrorCode.SESSION_NOT_FOUND,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verify(deleted, never()).getStatus();
        verifyNoInteractions(attendanceRepository, qrTokenProvider);
    }

    @ParameterizedTest
    @EnumSource(value = SessionStatus.class, names = "IN_PROGRESS", mode = EnumSource.Mode.EXCLUDE)
    void aNonInProgressSessionCannotAcceptCheckIns(SessionStatus status) {
        Session session = mock(Session.class);
        when(session.getDeletedAt()).thenReturn(null);
        when(session.getStatus()).thenReturn(status);
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));

        assertCheckInError(CheckInErrorCode.SESSION_NOT_IN_PROGRESS,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verifyNoInteractions(attendanceRepository, qrTokenProvider);
    }

    @Test
    void aSessionWhoseActualStartIsStillInTheFutureCannotAcceptCheckIns() {
        Session session = inProgressSession(CHECKED_IN_AT.plus(1, ChronoUnit.MICROS));
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(attendanceRepository.isTargetForUpdate(SESSION_ID, MEMBER_ID)).thenReturn(true);

        assertCheckInError(CheckInErrorCode.SESSION_NOT_IN_PROGRESS,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, request()));

        verify(attendanceRepository).isTargetForUpdate(SESSION_ID, MEMBER_ID);
        verifyNoMoreInteractions(attendanceRepository);
        verifyNoInteractions(qrTokenProvider);
    }

    @Test
    void invalidArgumentsAreRejectedBeforeAcquiringTheSessionLock() {
        assertCheckInError(CommonErrorCode.VALIDATION_ERROR, () -> service.checkIn(null, MEMBER_ID, request()));
        assertCheckInError(CommonErrorCode.VALIDATION_ERROR, () -> service.checkIn(SESSION_ID, MEMBER_ID, null));
        assertCheckInError(CommonErrorCode.VALIDATION_ERROR,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, new CheckInReqDTO(" ")));
        assertCheckInError(CommonErrorCode.VALIDATION_ERROR,
                () -> service.checkIn(SESSION_ID, MEMBER_ID, new CheckInReqDTO("x".repeat(4097))));

        verifyNoInteractions(sessionRepository, attendanceRepository, qrTokenProvider);
    }

    private void stubSuccessfulInsert(Session session, AttendanceStatus status, long checkInId) {
        stubSessionAndTarget(session);
        when(attendanceRepository.findAttendanceStatus(SESSION_ID, MEMBER_ID)).thenReturn(Optional.empty());
        when(attendanceRepository.insertAttendance(SESSION_ID, MEMBER_ID, status, CHECKED_IN_AT))
                .thenReturn(Optional.of(checkInId));
    }

    private void stubSessionAndTarget(Session session) {
        when(sessionRepository.findByIdForUpdate(SESSION_ID)).thenReturn(Optional.of(session));
        when(attendanceRepository.isTargetForUpdate(SESSION_ID, MEMBER_ID)).thenReturn(true);
    }

    private Session inProgressSession(OffsetDateTime startedAt) {
        Session session = mock(Session.class);
        when(session.getDeletedAt()).thenReturn(null);
        when(session.getStatus()).thenReturn(SessionStatus.IN_PROGRESS);
        when(session.getStartedAt()).thenReturn(startedAt);
        return session;
    }

    private CheckInReqDTO request() {
        return new CheckInReqDTO("qr-token");
    }

    private void assertCheckInError(Object expectedErrorCode, Runnable action) {
        BusinessException exception = assertThrows(BusinessException.class, action::run);
        assertEquals(expectedErrorCode, exception.getErrorCode());
    }
}
