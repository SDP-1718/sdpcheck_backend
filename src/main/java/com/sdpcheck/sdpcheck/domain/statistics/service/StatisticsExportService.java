package com.sdpcheck.sdpcheck.domain.statistics.service;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.AttendanceStatisticsExportFile;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.repository.MemberStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class StatisticsExportService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final MemberStatisticsQueryRepository repository;
    private final AttendanceQueryRepository attendanceRepository;
    private final AttendanceStatisticsWorkbookWriter writer;
    private final Clock clock;

    @Autowired
    public StatisticsExportService(MemberStatisticsQueryRepository repository,
                                   AttendanceQueryRepository attendanceRepository,
                                   AttendanceStatisticsWorkbookWriter writer) {
        this(repository, attendanceRepository, writer, Clock.systemUTC());
    }

    StatisticsExportService(MemberStatisticsQueryRepository repository,
                            AttendanceQueryRepository attendanceRepository,
                            AttendanceStatisticsWorkbookWriter writer, Clock clock) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
        this.writer = writer;
        this.clock = clock;
    }

    public AttendanceStatisticsExportFile exportAttendanceStatistics(MemberStatisticsSort sort) {
        if (sort == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        var semester = attendanceRepository.findCurrentSemester()
                .filter(current -> current.periodCode() != null && !current.periodCode().isBlank())
                .orElseThrow(() -> new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));
        var members = repository.findAllMembers(semester.id(), sort);
        String periodCode = semester.periodCode().replaceAll("[^A-Za-z0-9_-]", "_");
        String date = LocalDate.now(clock.withZone(SEOUL)).format(DateTimeFormatter.BASIC_ISO_DATE);
        String filename = "sdpcheck_attendance_" + periodCode + "_" + date + ".xlsx";
        try {
            return new AttendanceStatisticsExportFile(filename, writer.write(members));
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, exception);
        }
    }
}
