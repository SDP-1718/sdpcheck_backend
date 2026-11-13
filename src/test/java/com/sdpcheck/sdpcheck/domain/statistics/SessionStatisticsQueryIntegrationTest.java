package com.sdpcheck.sdpcheck.domain.statistics;

import com.sdpcheck.sdpcheck.domain.statistics.dto.response.SessionAttendanceStatisticsItem;
import com.sdpcheck.sdpcheck.domain.statistics.service.SessionStatisticsQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class SessionStatisticsQueryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-08T10:00:00Z");

    @Autowired SessionStatisticsQueryService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    private UUID semesterId;

    @BeforeEach
    void setUp() {
        semesterId = UUID.randomUUID();
        semester(semesterId, "2026-2H", true);
    }

    @Test
    void countsPresentLateExplicitAbsentAndMissingRecordsWithoutWritingAttendance() {
        UUID sessionId = uuid(1);
        endedSession(sessionId, STARTS_AT);
        for (int i = 0; i < 6; i++) {
            long memberId = member("trend-" + i, i == 2 ? "ADMIN" : "MEMBER");
            target(sessionId, memberId);
            if (i < 2) {
                attendance(sessionId, memberId, "PRESENT");
            } else if (i == 2) {
                attendance(sessionId, memberId, "LATE");
            } else if (i == 3) {
                attendance(sessionId, memberId, "ABSENT");
            }
        }
        long recordsBefore = count("attendances");
        long targetsBefore = count("session_attendance_targets");

        var response = service.getSessionAttendanceStatistics();

        assertEquals("2026-2H", response.periodCode());
        assertEquals(1, response.sessions().size());
        var item = response.sessions().getFirst();
        assertEquals(sessionId, item.sessionId());
        assertEquals("정규 세션", item.sessionTitle());
        assertCounts(item, 6, 2, 1, 3, 50);
        assertEquals(recordsBefore, count("attendances"));
        assertEquals(targetsBefore, count("session_attendance_targets"));
    }

    @Test
    void retainsSessionsWithNoTargetsWithoutCountingTheLeftJoinPlaceholder() {
        endedSession(uuid(1), STARTS_AT);

        var item = service.getSessionAttendanceStatistics().sessions().getFirst();

        assertCounts(item, 0, 0, 0, 0, null);
        assertNull(item.attendanceRate());
    }

    @Test
    void zeroPercentWithTargetsIsDifferentFromNoTargetsAndRoundsTwoThirdsUp() {
        endedSession(uuid(1), STARTS_AT);
        endedSession(uuid(2), STARTS_AT.plusWeeks(1));
        for (int i = 0; i < 3; i++) {
            long memberId = member("rounding-" + i, "MEMBER");
            target(uuid(1), memberId);
            target(uuid(2), memberId);
            if (i < 2) {
                attendance(uuid(1), memberId, i == 0 ? "PRESENT" : "LATE");
            }
        }

        var sessions = service.getSessionAttendanceStatistics().sessions();

        assertCounts(sessions.get(0), 3, 1, 1, 1, 67);
        assertCounts(sessions.get(1), 3, 0, 0, 3, 0);
    }

    @Test
    void excludesIneligibleSessionsAndIncludesUnpublishedCompletedSessions() {
        endedSession(uuid(1), STARTS_AT);
        jdbc.update("UPDATE study_sessions SET is_published = false WHERE id = ?", uuid(1));
        session(uuid(2), semesterId, STARTS_AT, null, null);
        session(uuid(3), semesterId, STARTS_AT, STARTS_AT, null);
        endedSession(uuid(4), STARTS_AT);
        jdbc.update("UPDATE study_sessions SET cancelled_at = ? WHERE id = ?", STARTS_AT, uuid(4));
        endedSession(uuid(5), STARTS_AT);
        jdbc.update("UPDATE study_sessions SET deleted_at = ? WHERE id = ?", STARTS_AT, uuid(5));
        endedSession(uuid(6), STARTS_AT);
        jdbc.update("UPDATE study_sessions SET is_regular = false WHERE id = ?", uuid(6));
        UUID previousSemester = UUID.randomUUID();
        semester(previousSemester, "2026-1H", false);
        session(uuid(7), previousSemester, STARTS_AT, STARTS_AT, STARTS_AT.plusHours(1));
        long memberId = member("filter-target", "MEMBER");
        for (int i = 1; i <= 7; i++) {
            target(uuid(i), memberId);
            attendance(uuid(i), memberId, "PRESENT");
        }
        jdbc.update("INSERT INTO events (id, title, starts_at, created_by) VALUES (?, '일반 일정', ?, ?)",
                uuid(8), STARTS_AT, memberId);

        var sessions = service.getSessionAttendanceStatistics().sessions();

        assertEquals(List.of(uuid(1)), sessions.stream().map(SessionAttendanceStatisticsItem::sessionId).toList());
        assertCounts(sessions.getFirst(), 1, 1, 0, 0, 100);
    }

    @Test
    void ordersByScheduledTimeThenIdAndReturnsSeoulTime() {
        endedSession(uuid(1), STARTS_AT.plusDays(1));
        endedSession(uuid(3), STARTS_AT);
        endedSession(uuid(2), STARTS_AT);
        jdbc.update("UPDATE study_sessions SET started_at = ?, ended_at = ? WHERE id = ?",
                STARTS_AT.plusDays(2), STARTS_AT.plusDays(2).plusHours(1), uuid(2));

        var sessions = service.getSessionAttendanceStatistics().sessions();

        assertEquals(List.of(uuid(2), uuid(3), uuid(1)),
                sessions.stream().map(SessionAttendanceStatisticsItem::sessionId).toList());
        assertEquals(OffsetDateTime.parse("2026-10-08T19:00:00+09:00"), sessions.getFirst().startsAt());
        assertTrue(sessions.stream().allMatch(item -> item.startsAt().getOffset().equals(ZoneOffset.ofHours(9))));
    }

    @Test
    void usesSessionTargetsRegardlessOfCurrentRosterAndMemberProfile() {
        endedSession(uuid(1), STARTS_AT);
        long memberId = member("target-outside-roster", "MEMBER");
        long rosterOnlyId = member("roster-only", "ADMIN");
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)", semesterId, rosterOnlyId);
        target(uuid(1), memberId);
        attendance(uuid(1), memberId, "LATE");
        var before = service.getSessionAttendanceStatistics();

        jdbc.update("UPDATE members SET role = 'ADMIN', generation = 99 WHERE id = ?", memberId);
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)", semesterId, memberId);
        var after = service.getSessionAttendanceStatistics();

        assertEquals(before, after);
        assertCounts(after.sessions().getFirst(), 1, 0, 1, 0, 100);
    }

    @Test
    void returnsAnEmptySeriesWhenThereAreNoEligibleSessions() {
        session(uuid(1), semesterId, STARTS_AT, null, null);

        var response = service.getSessionAttendanceStatistics();

        assertEquals("2026-2H", response.periodCode());
        assertEquals(List.of(), response.sessions());
    }

    @Test
    void swaggerPublishesTheSeriesOperationWithBearerAuthenticationAndResponseFields() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/statistics/sessions'].get.operationId")
                        .value("getSessionAttendanceStatistics"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/statistics/sessions'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.components.schemas.SessionAttendanceStatisticsItem.properties.totalCount").exists())
                .andExpect(jsonPath("$.components.schemas.SessionAttendanceStatisticsItem.properties.attendanceRate").exists())
                .andExpect(jsonPath("$.components.schemas.SessionAttendanceStatisticsResponse.properties.sessions.items['$ref']")
                        .value("#/components/schemas/SessionAttendanceStatisticsItem"));
    }

    private void assertCounts(SessionAttendanceStatisticsItem item, long total, long present, long late,
                              long absent, Integer rate) {
        assertEquals(total, item.totalCount());
        assertEquals(present, item.presentCount());
        assertEquals(late, item.lateCount());
        assertEquals(absent, item.absentCount());
        assertEquals(rate, item.attendanceRate());
        assertEquals(item.totalCount(), item.presentCount() + item.lateCount() + item.absentCount());
    }

    private long member(String loginId, String role) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'test-password-hash', '회원', 18) RETURNING id
                """, Long.class, role, loginId);
    }

    private void semester(UUID id, String periodCode, boolean current) {
        jdbc.update("""
                INSERT INTO semesters (id, name, period_code, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', ?, DATE '2026-07-01', DATE '2026-12-31', ?)
                """, id, periodCode, current);
    }

    private void endedSession(UUID id, OffsetDateTime startsAt) {
        session(id, semesterId, startsAt, startsAt.plusMinutes(5), startsAt.plusHours(1));
    }

    private void session(UUID id, UUID termId, OffsetDateTime startsAt, OffsetDateTime startedAt, OffsetDateTime endedAt) {
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, semester_id, started_at, ended_at)
                VALUES (?, '정규 세션', ?, ?, ?, ?)
                """, id, startsAt, termId, startedAt, endedAt);
    }

    private void target(UUID sessionId, long memberId) {
        jdbc.update("INSERT INTO session_attendance_targets (session_id, member_id) VALUES (?, ?)", sessionId, memberId);
    }

    private void attendance(UUID sessionId, long memberId, String status) {
        jdbc.update("INSERT INTO attendances (session_id, member_id, status, checked_in_at) VALUES (?, ?, ?, ?)",
                sessionId, memberId, status, "ABSENT".equals(status) ? null : STARTS_AT.plusMinutes(10));
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("62626262-6262-6262-6262-%012d".formatted(suffix));
    }
}
