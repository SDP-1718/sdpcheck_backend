package com.sdpcheck.sdpcheck.domain.attendance.service;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryItem;
import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryPageResponse;
import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceSummaryResponse;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AttendanceQueryService {
    public static final int MAX_PAGE_SIZE = 100;
    private final AttendanceQueryRepository repository;

    public AttendanceQueryService(AttendanceQueryRepository repository) {
        this.repository = repository;
    }

    public AttendanceHistoryPageResponse getMyAttendances(long memberId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        UUID semesterId = repository.findCurrentSemesterId()
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));
        long totalElements = repository.countHistory(memberId, semesterId);
        int totalPages = Math.toIntExact(Math.ceilDiv(totalElements, (long) size));
        List<AttendanceHistoryItem> items = repository.findHistory(memberId, semesterId, page, size);
        return new AttendanceHistoryPageResponse(items, page, size, totalElements, totalPages,
                (long) page + 1 < totalPages);
    }

    public AttendanceSummaryResponse getMyAttendanceSummary(long memberId) {
        var semester = repository.findCurrentSemester()
                .filter(current -> current.periodCode() != null && !current.periodCode().isBlank())
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));
        var counts = repository.countAttendance(memberId, semester.id());
        Integer attendanceRate = counts.totalSessions() == 0 ? null
                : BigDecimal.valueOf(counts.presentCount() + counts.lateCount())
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(counts.totalSessions()), 0, RoundingMode.HALF_UP)
                        .intValueExact();
        return new AttendanceSummaryResponse(semester.periodCode(), attendanceRate, counts.presentCount(),
                counts.lateCount(), counts.absentCount(), counts.totalSessions());
    }
}
