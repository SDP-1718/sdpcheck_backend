package com.sdpcheck.sdpcheck.domain.session;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class SessionIntegrationTest {
    private static final String BASE = "/api/v1/admin/sessions";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwtProvider;

    private final LocalDate future = LocalDate.now().plusDays(7);
    private String adminToken;
    private String memberToken;

    @BeforeEach
    void setUp() {
        adminToken = "Bearer " + jwtProvider.createAccessToken(insertMember("ADMIN"));
        memberToken = "Bearer " + jwtProvider.createAccessToken(insertMember("MEMBER"));
    }

    @Test
    void managesSessionLifecycleOnStudySessions() throws Exception {
        String created = create("정기 세션", future, "19:00", "21:00", "공학관 101")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("SESSION2011"))
                .andExpect(jsonPath("$.result.sessionDate").value(future.toString()))
                .andExpect(jsonPath("$.result.startTime").value("19:00"))
                .andExpect(jsonPath("$.result.endTime").value("21:00"))
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(JsonPath.read(created, "$.result.sessionId"));

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT starts_at, ends_at FROM study_sessions WHERE id = ?", id);
        assertEquals(future.atTime(10, 0).toInstant(ZoneOffset.UTC),
                ((Timestamp) row.get("starts_at")).toInstant());
        assertEquals(future.atTime(12, 0).toInstant(ZoneOffset.UTC),
                ((Timestamp) row.get("ends_at")).toInstant());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM schedule_items WHERE id = ? AND type = 'SESSION'", Integer.class, id));
        assertNull(jdbc.queryForObject("SELECT semester_id FROM study_sessions WHERE id = ?", UUID.class, id));

        admin(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정됨\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.title").value("수정됨"))
                .andExpect(jsonPath("$.result.endTime").value("21:00"));
        admin(post(BASE + "/" + id + "/end"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION4093"));
        admin(post(BASE + "/" + id + "/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"));
        admin(delete(BASE + "/" + id))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION4094"));
        admin(post(BASE + "/" + id + "/end"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION2003"))
                .andExpect(jsonPath("$.result.status").value("COMPLETED"));
        admin(post(BASE + "/" + id + "/start"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION4092"));

        Map<String, Object> ended = jdbc.queryForMap(
                "SELECT title, started_at, ended_at FROM study_sessions WHERE id = ?", id);
        assertEquals("수정됨", ended.get("title"));
        assertNotNull(ended.get("started_at"));
        assertNotNull(ended.get("ended_at"));
    }

    @Test
    void returnsSessionDetail() throws Exception {
        UUID id = UUID.fromString(JsonPath.read(create("9월 4주차 정기 세션", future, "18:00", "20:00", "정보과학관 301호")
                .andReturn().getResponse().getContentAsString(), "$.result.sessionId"));

        admin(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SESSION2001"))
                .andExpect(jsonPath("$.message").value("세션 상세 조회에 성공했습니다."))
                .andExpect(jsonPath("$.result.sessionId").value(id.toString()))
                .andExpect(jsonPath("$.result.title").value("9월 4주차 정기 세션"))
                .andExpect(jsonPath("$.result.sessionDate").value(future.toString()))
                .andExpect(jsonPath("$.result.startTime").value("18:00"))
                .andExpect(jsonPath("$.result.endTime").value("20:00"))
                .andExpect(jsonPath("$.result.location").value("정보과학관 301호"))
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"));

        admin(post(BASE + "/" + id + "/start")).andExpect(status().isOk());
        admin(get(BASE + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"));

        admin(get(BASE + "/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("SESSION4041"));
        mvc.perform(get(BASE + "/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/" + id).header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void returnsNextSession() throws Exception {
        jdbc.update("DELETE FROM attendances");
        jdbc.update("DELETE FROM session_attendance_targets");
        jdbc.update("DELETE FROM study_sessions");
        admin(get(BASE + "/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SESSION2005"))
                .andExpect(jsonPath("$.result").value((Object) null));

        OffsetDateTime now = OffsetDateTime.now();
        insertSession("지난 세션", now.minusHours(3), now.minusHours(1), null);
        insertSession("삭제된 세션", now.plusHours(1), now.plusHours(2), "deleted_at");
        insertSession("취소된 세션", now.plusHours(1), now.plusHours(2), "cancelled_at");
        insertSession("모레 세션", now.plusDays(2), now.plusDays(2).plusHours(2), null);
        UUID tomorrow = insertSession("내일 세션", now.plusDays(1), now.plusDays(1).plusHours(2), null);
        admin(get(BASE + "/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.sessionId").value(tomorrow.toString()))
                .andExpect(jsonPath("$.result.status").value("SCHEDULED"));

        UUID late = insertSession("시작이 늦어진 세션", now.minusMinutes(10), now.plusMinutes(50), null);
        admin(get(BASE + "/next"))
                .andExpect(jsonPath("$.result.sessionId").value(late.toString()));

        admin(post(BASE + "/" + tomorrow + "/start")).andExpect(status().isOk());
        admin(get(BASE + "/next"))
                .andExpect(jsonPath("$.result.sessionId").value(tomorrow.toString()))
                .andExpect(jsonPath("$.result.status").value("IN_PROGRESS"));

        admin(post(BASE + "/" + tomorrow + "/end")).andExpect(status().isOk());
        admin(get(BASE + "/next"))
                .andExpect(jsonPath("$.result.sessionId").value(late.toString()));

        mvc.perform(get(BASE + "/next").header(HttpHeaders.AUTHORIZATION, memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void softDeletesScheduledSession() throws Exception {
        UUID id = UUID.fromString(JsonPath.read(create("삭제할 세션", future, "10:00", "11:00", "세미나실")
                .andReturn().getResponse().getContentAsString(), "$.result.sessionId"));

        admin(delete(BASE + "/" + id)).andExpect(status().isNoContent());
        admin(delete(BASE + "/" + id))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SESSION4041"));
        admin(post(BASE + "/" + id + "/start")).andExpect(status().isNotFound());
        admin(get(BASE + "/" + id)).andExpect(status().isNotFound());

        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM study_sessions WHERE id = ?",
                OffsetDateTime.class, id));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM schedule_items WHERE id = ?", Integer.class, id));
    }

    @Test
    void rejectsTooLongTextAndPastSchedule() throws Exception {
        create("가".repeat(100), future, "19:00", "21:00", "a".repeat(200)).andExpect(status().isCreated());
        create("가".repeat(101), future, "19:00", "21:00", "a")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4001"));
        create("세션", future, "19:00", "21:00", "a".repeat(201))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4001"));
        create("세션", LocalDate.now().minusDays(1), "19:00", "21:00", "a")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4003"));

        UUID id = UUID.fromString(JsonPath.read(create("세션", future, "19:00", "21:00", "a")
                .andReturn().getResponse().getContentAsString(), "$.result.sessionId"));
        admin(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionDate\":\"" + LocalDate.now().minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4003"));
        admin(patch(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content("{\"location\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4001"));
    }

    @Test
    void createsWithOptionalFieldsAndRepeatsWeeklyUntilSemesterEnd() throws Exception {
        admin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"장소 미정 세션\",\"sessionDate\":\"" + future + "\",\"startTime\":\"19:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.endTime").value((Object) null))
                .andExpect(jsonPath("$.result.location").value((Object) null))
                .andExpect(jsonPath("$.result.createdCount").value(1));

        String weekday = future.getDayOfWeek().name().substring(0, 3);
        String repeat = "{\"title\":\"반복 세션\",\"sessionDate\":\"" + future + "\",\"startTime\":\"19:00\","
                + "\"repeatWeekly\":true,\"repeatDays\":%s}";
        admin(post(BASE).contentType(MediaType.APPLICATION_JSON).content(repeat.formatted("[\"XYZ\"]")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4001"));
        admin(post(BASE).contentType(MediaType.APPLICATION_JSON).content(repeat.formatted("[]")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SESSION4001"));
        admin(post(BASE).contentType(MediaType.APPLICATION_JSON).content(repeat.formatted("[\"" + weekday + "\"]")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SESSION4099"));

        UUID semesterId = UUID.randomUUID();
        LocalDate semesterEnd = future.plusDays(20);
        jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on, is_current)
                VALUES (?, '2026-2', current_date - 30, ?, true)
                """, semesterId, semesterEnd);
        try {
            String secondDay = future.getDayOfWeek().plus(2).name().substring(0, 3);
            long expected = future.datesUntil(semesterEnd.plusDays(1))
                    .filter(date -> date.equals(future)
                            || date.getDayOfWeek() == future.getDayOfWeek()
                            || date.getDayOfWeek() == future.getDayOfWeek().plus(2))
                    .count();

            admin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                    .content(repeat.formatted("[\"" + weekday + "\",\"" + secondDay + "\"]")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.result.sessionDate").value(future.toString()))
                    .andExpect(jsonPath("$.result.createdCount").value((int) expected));
            assertEquals(expected, jdbc.queryForObject("""
                    SELECT count(*) FROM study_sessions
                    WHERE title = '반복 세션' AND semester_id = ? AND deleted_at IS NULL
                    """, Long.class, semesterId));
        } finally {
            jdbc.update("UPDATE study_sessions SET semester_id = NULL WHERE semester_id = ?", semesterId);
            jdbc.update("DELETE FROM semesters WHERE id = ?", semesterId);
        }
    }

    @Test
    void requiresAdmin() throws Exception {
        String body = body("세션", future, "19:00", "21:00", "a");
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).header(HttpHeaders.AUTHORIZATION, memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    private ResultActions create(String title, LocalDate date, String start, String end, String location)
            throws Exception {
        return admin(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content(body(title, date, start, end, location)));
    }

    private ResultActions admin(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, adminToken));
    }

    private static String body(String title, LocalDate date, String start, String end, String location) {
        return """
                {"title":"%s","sessionDate":"%s","startTime":"%s","endTime":"%s","location":"%s"}
                """.formatted(title, date, start, end, location);
    }

    private UUID insertSession(String title, OffsetDateTime startsAt, OffsetDateTime endsAt, String markedColumn) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO study_sessions (id, title, starts_at, ends_at, location) VALUES (?, ?, ?, ?, '세미나실')",
                id, title, startsAt, endsAt);
        if (markedColumn != null) {
            jdbc.update("UPDATE study_sessions SET " + markedColumn + " = now() WHERE id = ?", id);
        }
        return id;
    }

    private long insertMember(String role) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'hash', '테스트', 17)
                RETURNING id
                """, Long.class, role, "session-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
