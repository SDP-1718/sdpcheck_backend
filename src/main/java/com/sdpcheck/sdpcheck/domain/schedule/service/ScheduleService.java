package com.sdpcheck.sdpcheck.domain.schedule.service;

import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleListResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import com.sdpcheck.sdpcheck.domain.schedule.repository.ScheduleRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ScheduleService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final ScheduleRepository repository;

    public ScheduleService(ScheduleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request, long memberId) {
        String title = request.title().strip();
        if (title.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        return repository.create(UUID.randomUUID(), title, request.startsAt(), request.location(), memberId);
    }

    @Transactional(readOnly = true)
    public ScheduleListResponse list(LocalDate from, LocalDate to, String type, int page, int size) {
        if (page < 1 || size < 1 || size > 100 || (from == null) != (to == null)
                || (type != null && !type.equals("EVENT") && !type.equals("SESSION"))) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        if (from != null && (!from.isBefore(to) || ChronoUnit.DAYS.between(from, to) > 366)) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        OffsetDateTime fromInstant = from == null ? null : from.atStartOfDay(SEOUL).toOffsetDateTime();
        OffsetDateTime toInstant = to == null ? null : to.atStartOfDay(SEOUL).toOffsetDateTime();
        long total = repository.count(fromInstant, toInstant, type);
        long totalPages = (total / size) + (total % size == 0 ? 0 : 1);
        return new ScheduleListResponse(repository.find(fromInstant, toInstant, type, page, size),
                new ScheduleListResponse.Page(page, size, total, totalPages));
    }
}
