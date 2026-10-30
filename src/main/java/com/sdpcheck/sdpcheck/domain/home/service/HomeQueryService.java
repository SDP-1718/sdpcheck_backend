package com.sdpcheck.sdpcheck.domain.home.service;

import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.home.dto.response.HomeResponse;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeCheckInStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeSessionStatus;
import com.sdpcheck.sdpcheck.domain.home.repository.HomeQueryRepository;
import com.sdpcheck.sdpcheck.domain.home.repository.HomeQueryRepository.SessionCandidate;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class HomeQueryService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Duration NORMAL_CHECK_IN_WINDOW = Duration.ofMinutes(5);

    private final HomeQueryRepository repository;
    private final AttendanceQueryService attendanceService;
    private final Clock clock;

    public HomeQueryService(HomeQueryRepository repository, AttendanceQueryService attendanceService,
                            @Qualifier("homeClock") Clock clock) {
        this.repository = repository;
        this.attendanceService = attendanceService;
        this.clock = clock;
    }

    public HomeResponse getHome(long memberId) {
        OffsetDateTime serverTime = clock.instant().atZone(SEOUL).toOffsetDateTime();
        var member = repository.findMember(memberId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED));
        var summary = attendanceService.getMyAttendanceSummary(memberId);
        var statistics = new HomeResponse.Statistics(summary.periodCode(), summary.attendanceRate(),
                summary.presentCount() + summary.lateCount(), summary.totalSessionCount());
        var session = repository.findSession(memberId, serverTime)
                .map(candidate -> sessionInfo(candidate, serverTime)).orElse(null);
        return new HomeResponse(serverTime, member, session, statistics,
                repository.findLatestNotices(), repository.findUpcomingSchedules(serverTime));
    }

    private HomeResponse.SessionInfo sessionInfo(SessionCandidate candidate, OffsetDateTime serverTime) {
        HomeSessionStatus status = candidate.endedAt() != null ? HomeSessionStatus.ENDED
                : candidate.startedAt() != null ? HomeSessionStatus.IN_PROGRESS : HomeSessionStatus.SCHEDULED;
        HomeAttendanceStatus attendanceStatus = candidate.attendanceStatus() != null
                ? HomeAttendanceStatus.valueOf(candidate.attendanceStatus().name())
                : status == HomeSessionStatus.ENDED ? HomeAttendanceStatus.ABSENT
                : HomeAttendanceStatus.NOT_CHECKED_IN;
        HomeCheckInStatus checkInStatus = HomeCheckInStatus.UNAVAILABLE;
        if (status == HomeSessionStatus.IN_PROGRESS && candidate.attendanceStatus() == null
                && !serverTime.isBefore(candidate.startedAt())) {
            checkInStatus = serverTime.isAfter(candidate.startedAt().plus(NORMAL_CHECK_IN_WINDOW))
                    ? HomeCheckInStatus.LATE_AVAILABLE : HomeCheckInStatus.AVAILABLE;
        }
        return new HomeResponse.SessionInfo(candidate.id(), candidate.title(), candidate.startsAt(), status,
                attendanceStatus, checkInStatus, candidate.checkedInAt());
    }
}
