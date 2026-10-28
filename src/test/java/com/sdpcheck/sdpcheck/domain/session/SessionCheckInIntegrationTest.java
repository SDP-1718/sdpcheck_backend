package com.sdpcheck.sdpcheck.domain.session;

import com.jayway.jsonpath.JsonPath;
import com.sdpcheck.sdpcheck.domain.session.service.SessionQrTokenProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SessionCheckInIntegrationTest {
    private static final String ADMIN_BASE = "/api/v1/admin/sessions";
    private static final String CHECK_IN = "/api/v1/sessions/check-in";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwtProvider;
    @Autowired SessionQrTokenProvider qrTokenProvider;

    private long adminId;
    private long memberId;
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        adminId = insertMember("ADMIN");
        memberId = insertMember("MEMBER");
        adminToken = bearer(adminId);
        memberToken = bearer(memberId);
    }

    @Test
    void issuesQrOnlyForInProgressSessionAndRegistersTargetsOnStart() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusHours(1), now.plusHours(3));

        mvc.perform(get(ADMIN_BASE + "/" + sessionId + "/qr").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4093"));

        start(sessionId);
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM session_attendance_targets WHERE session_id = ? AND member_id = ?
                """, Integer.class, sessionId, memberId));
        assertEquals(0, jdbc.queryForObject("""
                SELECT count(*) FROM session_attendance_targets WHERE session_id = ? AND member_id = ?
                """, Integer.class, sessionId, adminId));

        mvc.perform(get(ADMIN_BASE + "/" + sessionId + "/qr").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION2006"))
                .andExpect(jsonPath("$.result.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.result.qrToken").isString())
                .andExpect(jsonPath("$.result.expiresAt").isString());
        mvc.perform(get(ADMIN_BASE + "/" + sessionId + "/qr").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void checksInAsPresentBeforeScheduledStartAndRejectsDuplicate() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusMinutes(10), now.plusHours(2));
        start(sessionId);
        String qr = issueQr(sessionId);

        checkIn(memberToken, qr)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION2007"))
                .andExpect(jsonPath("$.result.sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.result.status").value("PRESENT"));
        assertEquals("PRESENT", jdbc.queryForObject(
                "SELECT status FROM attendances WHERE session_id = ? AND member_id = ?",
                String.class, sessionId, memberId));

        checkIn(memberToken, issueQr(sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4096"));
    }

    @Test
    void checksInAsLateWithinTwentyMinutesAndClosesAfterwards() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID lateSession = insertSession(now.minusMinutes(10), now.plusMinutes(50));
        start(lateSession);
        checkIn(memberToken, issueQr(lateSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("LATE"));

        UUID closedSession = insertSession(now.minusMinutes(21), now.plusMinutes(40));
        start(closedSession);
        checkIn(memberToken, issueQr(closedSession))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4097"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM attendances WHERE session_id = ?", Integer.class, closedSession));
    }

    @Test
    void rejectsInvalidExpiredOrForeignTokens() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusMinutes(10), now.plusHours(2));
        start(sessionId);

        checkIn(memberToken, "not-a-token")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION4004"));
        checkIn(memberToken, qrTokenProvider.createToken(sessionId, now.minusSeconds(30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION4004"));
        checkIn(memberToken, jwtProvider.createAccessToken(memberId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION4004"));
        mvc.perform(post(CHECK_IN).contentType(MediaType.APPLICATION_JSON).content("{}")
                        .header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SESSION4004"));

        String qr = issueQr(sessionId);
        mvc.perform(get(ADMIN_BASE + "/next").header(HttpHeaders.AUTHORIZATION, "Bearer " + qr))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(CHECK_IN).contentType(MediaType.APPLICATION_JSON).content(body(qr)))
                .andExpect(status().isUnauthorized());

        // A QR that expired a few seconds ago is still accepted while the scan is being submitted.
        checkIn(memberToken, qrTokenProvider.createToken(sessionId, now.minusSeconds(5)))
                .andExpect(status().isOk());
    }

    @Test
    void checkInAppearsInMyAttendanceHistoryForCurrentSemester() throws Exception {
        UUID semesterId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on, is_current)
                VALUES (?, '2026-2', current_date - 30, current_date + 60, true)
                """, semesterId);
        String created = mvc.perform(post(ADMIN_BASE).header(HttpHeaders.AUTHORIZATION, adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"정기 세션","sessionDate":"%s","startTime":"19:00","endTime":"21:00","location":"세미나실"}
                                """.formatted(LocalDate.now().plusDays(1))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(JsonPath.read(created, "$.result.sessionId"));
        assertEquals(semesterId, jdbc.queryForObject(
                "SELECT semester_id FROM study_sessions WHERE id = ?", UUID.class, sessionId));

        start(sessionId);
        checkIn(memberToken, issueQr(sessionId)).andExpect(status().isOk());
        mvc.perform(post(ADMIN_BASE + "/" + sessionId + "/end").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/members/me/attendances").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.items[0].sessionId").value(sessionId.toString()))
                .andExpect(jsonPath("$.result.items[0].attendanceStatus").value("PRESENT"));
    }

    @Test
    void rejectsNonTargetAndEndedSession() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusMinutes(10), now.plusHours(2));
        start(sessionId);
        String qr = issueQr(sessionId);

        String lateJoiner = bearer(insertMember("MEMBER"));
        checkIn(lateJoiner, qr)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION4031"));
        checkIn(adminToken, qr)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SESSION4031"));

        mvc.perform(post(ADMIN_BASE + "/" + sessionId + "/end").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        checkIn(memberToken, qr)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4092"));
    }

    @Test
    void returnsLiveAttendanceWhileCheckInIsOpen() throws Exception {
        long lateMemberId = insertMember("MEMBER");
        long waitingMemberId = insertMember("MEMBER");
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusMinutes(10), now.plusHours(2));
        start(sessionId);
        checkIn(memberToken, issueQr(sessionId)).andExpect(status().isOk());
        jdbc.update("""
                INSERT INTO attendances (session_id, member_id, status, checked_in_at)
                VALUES (?, ?, 'LATE', now())
                """, sessionId, lateMemberId);

        int total = jdbc.queryForObject(
                "SELECT count(*) FROM session_attendance_targets WHERE session_id = ?", Integer.class, sessionId);
        double rate = Math.round(2 * 1000.0 / total) / 10.0;

        liveAttendance(sessionId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION2008"))
                .andExpect(jsonPath("$.result.sessionStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.checkInClosesAt").isString())
                .andExpect(jsonPath("$.result.summary.totalCount").value(total))
                .andExpect(jsonPath("$.result.summary.presentCount").value(1))
                .andExpect(jsonPath("$.result.summary.lateCount").value(1))
                .andExpect(jsonPath("$.result.summary.notCheckedInCount").value(total - 2))
                .andExpect(jsonPath("$.result.summary.absentCount").value(0))
                .andExpect(jsonPath("$.result.summary.attendanceRate").value(rate))
                .andExpect(jsonPath("$.result.members.length()").value(total))
                .andExpect(memberStatus(memberId, "PRESENT"))
                .andExpect(memberStatus(lateMemberId, "LATE"))
                .andExpect(memberStatus(waitingMemberId, "NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.result.members[?(@.memberId == %d)].checkedInAt", memberId)
                        .value(contains(notNullValue())));
    }

    @Test
    void marksMissingCheckInsAbsentAfterCheckInCloses() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.minusMinutes(30), now.plusMinutes(30));
        start(sessionId);

        liveAttendance(sessionId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.summary.presentCount").value(0))
                .andExpect(jsonPath("$.result.summary.notCheckedInCount").value(0))
                .andExpect(jsonPath("$.result.summary.attendanceRate").value(0.0))
                .andExpect(memberStatus(memberId, "ABSENT"));

        mvc.perform(post(ADMIN_BASE + "/" + sessionId + "/end").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        liveAttendance(sessionId, adminToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessionStatus").value("COMPLETED"))
                .andExpect(memberStatus(memberId, "ABSENT"));
        assertEquals(
                jdbc.queryForObject("SELECT count(*) FROM session_attendance_targets WHERE session_id = ?",
                        Integer.class, sessionId),
                jdbc.queryForObject("""
                        SELECT count(*) FROM attendances
                        WHERE session_id = ? AND status = 'ABSENT' AND checked_in_at IS NULL
                        """, Integer.class, sessionId));
    }

    @Test
    void rejectsLiveAttendanceBeforeStartOrForNonAdmin() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusHours(1), now.plusHours(3));

        liveAttendance(sessionId, adminToken)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4093"));
        liveAttendance(UUID.randomUUID(), adminToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION4041"));
        start(sessionId);
        liveAttendance(sessionId, memberToken).andExpect(status().isForbidden());
    }

    @Test
    void adminUpdatesAttendanceStatusManually() throws Exception {
        long otherMemberId = insertMember("MEMBER");
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusMinutes(10), now.plusHours(2));
        start(sessionId);
        checkIn(memberToken, issueQr(sessionId)).andExpect(status().isOk());
        OffsetDateTime checkedInAt = jdbc.queryForObject(
                "SELECT checked_in_at FROM attendances WHERE session_id = ? AND member_id = ?",
                OffsetDateTime.class, sessionId, memberId);

        updateAttendance(sessionId, memberId, adminToken, "{\"status\":\"LATE\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION2009"))
                .andExpect(jsonPath("$.result.memberId").value(memberId))
                .andExpect(jsonPath("$.result.status").value("LATE"));
        assertEquals(checkedInAt.toInstant(), jdbc.queryForObject(
                "SELECT checked_in_at FROM attendances WHERE session_id = ? AND member_id = ?",
                OffsetDateTime.class, sessionId, memberId).toInstant());

        updateAttendance(sessionId, memberId, adminToken, "{\"status\":\"ABSENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("ABSENT"))
                .andExpect(jsonPath("$.result.checkedInAt").value((Object) null));
        assertEquals("ABSENT", jdbc.queryForObject(
                "SELECT status FROM attendances WHERE session_id = ? AND member_id = ? AND checked_in_at IS NULL",
                String.class, sessionId, memberId));
        liveAttendance(sessionId, adminToken)
                .andExpect(memberStatus(memberId, "ABSENT"))
                .andExpect(memberStatus(otherMemberId, "NOT_CHECKED_IN"));
        checkIn(memberToken, issueQr(sessionId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4098"));

        mvc.perform(post(ADMIN_BASE + "/" + sessionId + "/end").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
        assertEquals("ABSENT", jdbc.queryForObject(
                "SELECT status FROM attendances WHERE session_id = ? AND member_id = ?",
                String.class, sessionId, otherMemberId));
        updateAttendance(sessionId, otherMemberId, adminToken, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("PRESENT"))
                .andExpect(jsonPath("$.result.checkedInAt").isString());
        liveAttendance(sessionId, adminToken)
                .andExpect(memberStatus(otherMemberId, "PRESENT"))
                .andExpect(memberStatus(memberId, "ABSENT"));
    }

    @Test
    void rejectsInvalidManualAttendanceUpdates() throws Exception {
        OffsetDateTime now = OffsetDateTime.now();
        UUID sessionId = insertSession(now.plusMinutes(10), now.plusHours(2));

        updateAttendance(sessionId, memberId, adminToken, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION4093"));
        start(sessionId);

        for (String body : new String[] {"{}", "{\"status\":\"NOT_CHECKED_IN\"}", "{\"status\":\"present\"}"}) {
            updateAttendance(sessionId, memberId, adminToken, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SESSION4001"));
        }
        updateAttendance(sessionId, adminId, adminToken, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION4042"));
        updateAttendance(UUID.randomUUID(), memberId, adminToken, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION4041"));
        updateAttendance(sessionId, memberId, memberToken, "{\"status\":\"PRESENT\"}")
                .andExpect(status().isForbidden());
    }

    private ResultActions updateAttendance(UUID sessionId, long targetMemberId, String bearer, String body)
            throws Exception {
        return mvc.perform(patch(ADMIN_BASE + "/" + sessionId + "/attendances/" + targetMemberId)
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions liveAttendance(UUID sessionId, String bearer) throws Exception {
        return mvc.perform(get(ADMIN_BASE + "/" + sessionId + "/check-ins")
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private static ResultMatcher memberStatus(long id, String status) {
        return jsonPath("$.result.members[?(@.memberId == %d)].status", id).value(contains(status));
    }

    private void start(UUID sessionId) throws Exception {
        mvc.perform(post(ADMIN_BASE + "/" + sessionId + "/start").header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk());
    }

    private String issueQr(UUID sessionId) throws Exception {
        String response = mvc.perform(get(ADMIN_BASE + "/" + sessionId + "/qr")
                        .header(HttpHeaders.AUTHORIZATION, adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.result.qrToken");
    }

    private ResultActions checkIn(String bearer, String qrToken) throws Exception {
        return mvc.perform(post(CHECK_IN).header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body(qrToken)));
    }

    private static String body(String qrToken) {
        return "{\"qrToken\":\"" + qrToken + "\"}";
    }

    private String bearer(long id) {
        return "Bearer " + jwtProvider.createAccessToken(id);
    }

    private UUID insertSession(OffsetDateTime startsAt, OffsetDateTime endsAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, ends_at, location)
                VALUES (?, '정기 세션', ?, ?, '세미나실')
                """, id, startsAt, endsAt);
        return id;
    }

    private long insertMember(String role) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'hash', '테스트', 17)
                RETURNING id
                """, Long.class, role, "checkin-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
