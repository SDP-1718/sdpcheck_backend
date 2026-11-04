package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.repository.SessionStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class SessionStatisticsQueryService {
    private final SessionStatisticsQueryRepository repository;
    private final AttendanceQueryRepository attendanceRepository;

    public SessionStatisticsQueryService(
            SessionStatisticsQueryRepository repository, AttendanceQueryRepository attendanceRepository) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
    }

    public SessionAttendanceStatisticsResponse getSessionAttendanceStatistics() {
        var semester = attendanceRepository.findCurrentSemester()
                .filter(current -> current.periodCode() != null && !current.periodCode().isBlank())
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));
        return new SessionAttendanceStatisticsResponse(semester.periodCode(), repository.findSessionStatistics(semester.id()));
    }
}
