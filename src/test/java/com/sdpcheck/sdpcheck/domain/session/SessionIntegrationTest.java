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

    private long insertMember(String role) {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES (?, ?, 'hash', '테스트', 17)
                RETURNING id
                """, Long.class, role, "session-" + UUID.randomUUID().toString().substring(0, 8));
    }
}
