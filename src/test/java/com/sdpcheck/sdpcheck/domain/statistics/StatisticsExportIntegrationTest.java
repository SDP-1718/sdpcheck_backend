package com.sdpcheck.sdpcheck.domain.statistics;

import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.AttendanceStatisticsExportFile;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.MemberAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.repository.MemberStatisticsQueryRepository;
import com.sdpcheck.sdpcheck.domain.statistics.service.MemberStatisticsQueryService;
import com.sdpcheck.sdpcheck.domain.statistics.service.StatisticsExportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;

@SpringBootTest(properties = "spring.config.import=")
@Testcontainers
@ActiveProfiles("test")
@Transactional
class StatisticsExportIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");
    private static final List<String> HEADERS = List.of(
            "회원 ID", "이름", "기수", "출석", "지각", "결석", "출석 대상 세션", "출석률(%)", "이탈주의 여부");

    @Autowired StatisticsExportService exportService;
    @Autowired MemberStatisticsQueryService memberService;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean MemberStatisticsQueryRepository memberRepository;

    private UUID semesterId;

    @BeforeEach
    void setUp() {
        semesterId = UUID.randomUUID();
        semester(semesterId, "2026-2H", true);
    }

    @Test
    void exportsEveryRosterMemberBeyondTheMemberApiPageLimitWithTheWorkbookHeader() throws Exception {
        for (int index = 1; index <= 51; index++) {
            long memberId = member("export-member-" + index, "member-%02d".formatted(index), Role.MEMBER, 10 + index);
            roster(memberId);
        }

        AttendanceStatisticsExportFile file = exportService.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC);
        WorkbookRows workbook = workbook(file);

        assertTrue(file.filename().matches("sdpcheck_attendance_2026-2H_\\d{8}\\.xlsx"));
        assertEquals("회원별 출석 통계", workbook.sheetName());
        assertEquals(HEADERS, workbook.headers());
        assertTrue(workbook.frozenHeader());
        assertEquals(51, workbook.rows().size());
        assertEquals("member-01", workbook.rows().getFirst().name());
        assertEquals("member-51", workbook.rows().getLast().name());
        assertEquals(CellType.BLANK, workbook.rows().getFirst().rateCellType());
        assertNull(workbook.rows().getFirst().attendanceRate());
        assertEquals("집계 전", workbook.rows().getFirst().risk());
    }

    @Test
    void exportMatchesEveryPagedMemberApiRowAndRawRateOrderForAllSorts() throws Exception {
        long rawBelowSeventy = member("export-raw-below", "zeta", Role.MEMBER, 17);
        long exactSeventy = member("export-raw-at", "alpha", Role.MEMBER, 17);
        long noTarget = member("export-raw-none", "middle", Role.ADMIN, 18);
        roster(rawBelowSeventy);
        roster(exactSeventy);
        roster(noTarget);
        for (int suffix = 1; suffix <= 210; suffix++) {
            endedSession(uuid(suffix), semesterId);
            target(uuid(suffix), suffix <= 200 ? rawBelowSeventy : exactSeventy);
            if (suffix <= 139 || suffix >= 201 && suffix <= 207) {
                checkIn(uuid(suffix), suffix <= 200 ? rawBelowSeventy : exactSeventy, "PRESENT");
            }
        }

        for (MemberStatisticsSort sort : MemberStatisticsSort.values()) {
            List<MemberAttendanceStatisticsItem> apiRows = allPagedRows(sort);
            List<ExportRow> exportRows = workbook(exportService.exportAttendanceStatistics(sort)).rows();

            assertEquals(apiRows.size(), exportRows.size());
            for (int index = 0; index < apiRows.size(); index++) {
                assertMatches(apiRows.get(index), exportRows.get(index));
            }
        }

        List<ExportRow> descending = workbook(exportService.exportAttendanceStatistics(
                MemberStatisticsSort.ATTENDANCE_RATE_DESC)).rows();
        assertEquals(List.of(Long.toString(exactSeventy), Long.toString(rawBelowSeventy), Long.toString(noTarget)),
                descending.stream().map(ExportRow::memberId).toList());
        assertEquals(70, descending.get(0).attendanceRate());
        assertEquals("해당 없음", descending.get(0).risk());
        assertEquals(70, descending.get(1).attendanceRate());
        assertEquals("주의", descending.get(1).risk());
        assertEquals(CellType.BLANK, descending.get(2).rateCellType());
        assertEquals("집계 전", descending.get(2).risk());
    }

    @Test
    void exportsOnlyCurrentEligibleTargetSessionsAndKeepsPrivateCompletedSessions() throws Exception {
        long rosterMember = member("export-filter-roster", "집계 회원", Role.MEMBER, 20);
        long outsideMember = member("export-filter-outside", "미배정 회원", Role.MEMBER, 20);
        UUID otherSemesterId = UUID.randomUUID();
        semester(otherSemesterId, "2026-1H", false);
        roster(rosterMember);

        endedSession(uuid(1), semesterId);
        target(uuid(1), rosterMember);
        checkIn(uuid(1), rosterMember, "PRESENT");
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(1));
        scheduledSession(uuid(2), semesterId);
        inProgressSession(uuid(3), semesterId);
        endedSession(uuid(4), semesterId);
        jdbc.update("UPDATE study_sessions SET cancelled_at = ? WHERE id = ?", STARTS_AT, uuid(4));
        endedSession(uuid(5), semesterId);
        jdbc.update("UPDATE study_sessions SET deleted_at = ? WHERE id = ?", STARTS_AT, uuid(5));
        endedSession(uuid(6), semesterId);
        jdbc.update("UPDATE study_sessions SET is_regular = false WHERE id = ?", uuid(6));
        endedSession(uuid(7), otherSemesterId);
        endedSession(uuid(8), semesterId);
        for (int suffix = 2; suffix <= 7; suffix++) {
            target(uuid(suffix), rosterMember);
        }
        target(uuid(8), outsideMember);
        endedSession(uuid(9), semesterId);
        target(uuid(9), rosterMember);
        checkIn(uuid(9), rosterMember, "LATE");
        endedSession(uuid(10), semesterId);
        target(uuid(10), rosterMember);

        ExportRow row = workbook(exportService.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC)).rows().getFirst();

        assertEquals(Long.toString(rosterMember), row.memberId());
        assertEquals(1, row.presentCount());
        assertEquals(1, row.lateCount());
        assertEquals(1, row.absentCount());
        assertEquals(3, row.totalSessionCount());
        assertEquals(67, row.attendanceRate());
        assertEquals("주의", row.risk());
    }

    @Test
    void noRosterMembersProducesAHeaderOnlyWorkbook() throws Exception {
        WorkbookRows workbook = workbook(exportService.exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC));

        assertEquals(HEADERS, workbook.headers());
        assertTrue(workbook.rows().isEmpty());
        assertEquals(0, workbook.lastRowNum());
    }

    @Test
    void exportDoesNotCreateAttendanceRowsAndRepresentsAnUnattendedTargetAsZeroRate() throws Exception {
        long rosterMember = member("export-read-only", "무출석 회원", Role.MEMBER, 21);
        roster(rosterMember);
        endedSession(uuid(1), semesterId);
        target(uuid(1), rosterMember);
        long attendanceBefore = jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class);

        WorkbookRows first = workbook(exportService.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC));
        WorkbookRows second = workbook(exportService.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC));
        ExportRow row = first.rows().getFirst();

        assertEquals(first.rows(), second.rows());
        assertEquals(0, row.presentCount());
        assertEquals(0, row.lateCount());
        assertEquals(1, row.absentCount());
        assertEquals(1, row.totalSessionCount());
        assertEquals(CellType.NUMERIC, row.rateCellType());
        assertEquals(0, row.attendanceRate());
        assertEquals("주의", row.risk());
        assertEquals(attendanceBefore, jdbc.queryForObject("SELECT count(*) FROM attendances", Long.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void exportCanStartItsOwnReadOnlyRepeatableReadTransaction() throws Exception {
        try {
            WorkbookRows workbook = workbook(exportService.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC));
            assertEquals(HEADERS, workbook.headers());
            assertTrue(workbook.rows().isEmpty());
        } finally {
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void exportKeepsTheRepeatableReadSnapshotWhenAMemberChangesBetweenSemesterLookupAndRowQuery() throws Exception {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.setReadOnly(false);
        Long memberId = null;
        AtomicBoolean hookInvoked = new AtomicBoolean();
        try {
            memberId = requiresNew.execute(status -> {
                long id = member("export-snapshot-member", "스냅샷 이전", Role.MEMBER, 22);
                roster(id);
                return id;
            });
            long committedMemberId = memberId;
            doAnswer(invocation -> {
                hookInvoked.set(true);
                assertEquals("repeatable read", jdbc.queryForObject("SHOW transaction_isolation", String.class));
                assertEquals("on", jdbc.queryForObject("SHOW transaction_read_only", String.class));
                requiresNew.executeWithoutResult(status -> jdbc.update(
                        "UPDATE members SET name = '스냅샷 이후' WHERE id = ?", committedMemberId));
                return invocation.callRealMethod();
            }).when(memberRepository).findAllMembers(eq(semesterId), eq(MemberStatisticsSort.NAME_ASC));

            WorkbookRows workbook = workbook(exportService.exportAttendanceStatistics(MemberStatisticsSort.NAME_ASC));

            assertTrue(hookInvoked.get());
            assertEquals("스냅샷 이전", workbook.rows().getFirst().name());
            assertEquals("스냅샷 이후", jdbc.queryForObject(
                    "SELECT name FROM members WHERE id = ?", String.class, committedMemberId));
        } finally {
            doCallRealMethod().when(memberRepository)
                    .findAllMembers(any(UUID.class), any(MemberStatisticsSort.class));
            Long cleanupMemberId = memberId;
            requiresNew.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM semester_members WHERE semester_id = ?", semesterId);
                if (cleanupMemberId != null) {
                    jdbc.update("DELETE FROM members WHERE id = ?", cleanupMemberId);
                }
                jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
            });
        }
    }

    private List<MemberAttendanceStatisticsItem> allPagedRows(MemberStatisticsSort sort) {
        List<MemberAttendanceStatisticsItem> all = new ArrayList<>();
        for (int page = 0; ; page++) {
            var response = memberService.getMemberAttendanceStatistics(page, 1, sort);
            all.addAll(response.members());
            if (!response.hasNext()) {
                return all;
            }
        }
    }

    private WorkbookRows workbook(AttendanceStatisticsExportFile file) throws IOException {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            for (int column = 0; column < header.getLastCellNum(); column++) {
                headers.add(header.getCell(column).getStringCellValue());
            }
            List<ExportRow> rows = new ArrayList<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                rows.add(exportRow(sheet.getRow(index)));
            }
            boolean frozenHeader = sheet.getPaneInformation() != null && sheet.getPaneInformation().isFreezePane();
            return new WorkbookRows(sheet.getSheetName(), headers, rows, sheet.getLastRowNum(), frozenHeader);
        }
    }

    private ExportRow exportRow(Row row) {
        Cell rate = row.getCell(7);
        Integer attendanceRate = rate.getCellType() == CellType.BLANK ? null : (int) rate.getNumericCellValue();
        return new ExportRow(
                row.getCell(0).getStringCellValue(),
                row.getCell(1).getStringCellValue(),
                (int) row.getCell(2).getNumericCellValue(),
                (long) row.getCell(3).getNumericCellValue(),
                (long) row.getCell(4).getNumericCellValue(),
                (long) row.getCell(5).getNumericCellValue(),
                (long) row.getCell(6).getNumericCellValue(),
                attendanceRate,
                rate.getCellType(),
                row.getCell(8).getStringCellValue());
    }

    private void assertMatches(MemberAttendanceStatisticsItem expected, ExportRow actual) {
        assertEquals(Long.toString(expected.memberId()), actual.memberId());
        assertEquals(expected.name(), actual.name());
        assertEquals(expected.generation(), actual.generation());
        assertEquals(expected.presentCount(), actual.presentCount());
        assertEquals(expected.lateCount(), actual.lateCount());
        assertEquals(expected.absentCount(), actual.absentCount());
        assertEquals(expected.totalSessionCount(), actual.totalSessionCount());
        assertEquals(expected.attendanceRate(), actual.attendanceRate());
        assertEquals(expected.attendanceRate() == null ? CellType.BLANK : CellType.NUMERIC, actual.rateCellType());
        assertEquals(expected.isAtRisk() == null ? "집계 전" : expected.isAtRisk() ? "주의" : "해당 없음",
                actual.risk());
    }

    private long member(String loginId, String name, Role role, int generation) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'test-password-hash', ?, ?)
                RETURNING id
                """, Long.class, role.name(), loginId, name, generation);
    }

    private void semester(UUID id, String periodCode, boolean current) {
        jdbc.update("""
                INSERT INTO semesters (id, name, period_code, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', ?, DATE '2026-07-01', DATE '2026-12-31', ?)
                """, id, periodCode, current);
    }

    private void roster(long memberId) {
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)", semesterId, memberId);
    }

    private void endedSession(UUID id, UUID termId) {
        session(id, termId, STARTS_AT, STARTS_AT.plusMinutes(5), STARTS_AT.plusHours(1));
    }

    private void scheduledSession(UUID id, UUID termId) {
        session(id, termId, STARTS_AT, null, null);
    }

    private void inProgressSession(UUID id, UUID termId) {
        session(id, termId, STARTS_AT, STARTS_AT.plusMinutes(5), null);
    }

    private void session(UUID id, UUID termId, OffsetDateTime startsAt,
                         OffsetDateTime startedAt, OffsetDateTime endedAt) {
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, semester_id, started_at, ended_at)
                VALUES (?, '정규 세션', ?, ?, ?, ?)
                """, id, startsAt, termId, startedAt, endedAt);
    }

    private void target(UUID sessionId, long memberId) {
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)",
                sessionId, memberId);
    }

    private void checkIn(UUID sessionId, long memberId, String status) {
        jdbc.update("""
                INSERT INTO attendances (session_id, member_id, status, checked_in_at)
                VALUES (?, ?, ?, ?)
                """, sessionId, memberId, status, STARTS_AT.plusMinutes(10));
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }

    private record WorkbookRows(
            String sheetName,
            List<String> headers,
            List<ExportRow> rows,
            int lastRowNum,
            boolean frozenHeader
    ) {
    }

    private record ExportRow(
            String memberId,
            String name,
            int generation,
            long presentCount,
            long lateCount,
            long absentCount,
            long totalSessionCount,
            Integer attendanceRate,
            CellType rateCellType,
            String risk
    ) {
    }
}
