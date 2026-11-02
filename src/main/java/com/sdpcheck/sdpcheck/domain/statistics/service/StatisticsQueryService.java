package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.StatisticsSummaryResponse;
import com.sdpcheck.sdpcheck.domain.statistics.repository.StatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatisticsQueryService {
    private final StatisticsQueryRepository repository;
    private final AttendanceQueryRepository attendanceRepository;

    public StatisticsQueryService(StatisticsQueryRepository repository, AttendanceQueryRepository attendanceRepository) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
    }

    public StatisticsSummaryResponse getAdminStatistics() {
        var semester = attendanceRepository.findCurrentSemester()
                .filter(current -> current.periodCode() != null && !current.periodCode().isBlank())
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));
        var statistics = repository.aggregate(semester.id());
        return new StatisticsSummaryResponse(semester.periodCode(), statistics.averageAttendanceRate(),
                statistics.totalSessionCount(), statistics.activeMemberCount(), statistics.totalMemberCount());
    }
}
