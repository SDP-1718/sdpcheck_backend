package com.sdpcheck.sdpcheck.domain.schedule.service;

import com.sdpcheck.sdpcheck.domain.schedule.dto.response.SchedulePageResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.repository.ScheduleQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ScheduleQueryService {
    public static final int MAX_PAGE_SIZE = 100;
    private final ScheduleQueryRepository repository;

    public ScheduleQueryService(ScheduleQueryRepository repository) {
        this.repository = repository;
    }

    public SchedulePageResponse getSchedules(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        long totalElements = repository.countVisible();
        int totalPages = Math.toIntExact(Math.ceilDiv(totalElements, (long) size));
        List<ScheduleSummary> items = repository.findVisiblePage(page, size);
        return new SchedulePageResponse(items, page, size, totalElements, totalPages,
                (long) page + 1 < totalPages);
    }
}
