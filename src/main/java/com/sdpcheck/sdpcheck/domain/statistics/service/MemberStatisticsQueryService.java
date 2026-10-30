package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsResponse;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.repository.MemberStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MemberStatisticsQueryService {
    public static final int MAX_PAGE_SIZE = 50;
    private final MemberStatisticsQueryRepository repository;
    private final AttendanceQueryRepository attendanceRepository;

    public MemberStatisticsQueryService(
            MemberStatisticsQueryRepository repository, AttendanceQueryRepository attendanceRepository) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
    }

    public MemberAttendanceStatisticsResponse getMemberAttendanceStatistics(
            int page, int size, MemberStatisticsSort sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || sort == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        var semester = attendanceRepository.findCurrentSemester()
                .filter(current -> current.periodCode() != null && !current.periodCode().isBlank())
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));
        var rows = repository.findMembers(semester.id(), sort, size + 1, (long) page * size);
        boolean hasNext = rows.size() > size;
        var members = List.copyOf(rows.subList(0, Math.min(size, rows.size())));
        return new MemberAttendanceStatisticsResponse(semester.periodCode(), members, page, size, hasNext);
    }
}
