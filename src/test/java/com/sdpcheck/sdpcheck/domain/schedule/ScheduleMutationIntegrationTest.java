package com.sdpcheck.sdpcheck.domain.schedule;

import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.request.UpdateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class ScheduleMutationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    @Autowired ScheduleService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @Test
    void openApiDocumentsPatchFieldsAndEmptyDeleteResponse() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/schedules/{scheduleId}'].patch.operationId").value("updateSchedule"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/schedules/{scheduleId}'].delete.operationId").value("deleteSchedule"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/schedules/{scheduleId}'].delete.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateScheduleRequest.properties.title.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.UpdateScheduleRequest.properties.startsAt.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.UpdateScheduleRequest.properties.version.type").value("integer"))
                .andExpect(jsonPath("$.components.schemas.UpdateScheduleRequest.properties.changePresent").doesNotExist());
    }

    @Test
    void swaggerAndKakaoEndpointsRemainAvailableTogether() throws Exception {
        mvc.perform(get("/swagger-ui/index.html").header("Authorization", "Bearer stale-token"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer stale-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/kakao/login'].get").exists())
                .andExpect(jsonPath("$.paths['/login/oauth2/code/kakao'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/access-token'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh'].post.summary").value("로그인 토큰 갱신"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/signup']").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/v1/auth/check-login-id']").doesNotExist());
    }

    @Test
    void patchPreservesOmittedFieldsClearsLocationAndConvertsTimezone() {
        var event = create();
        var updated = service.update(event.id(), patch("{\"title\":\"  변경  \",\"version\":1}"));
        assertEquals("변경", updated.title());
        assertEquals(event.startsAt(), updated.startsAt());
        assertEquals(event.location(), updated.location());
        assertEquals(event.createdAt(), updated.createdAt());
        assertTrue(updated.updatedAt().isAfter(event.updatedAt()));
        assertEquals(2, updated.version());

        var cleared = service.update(event.id(), patch("{\"location\":null,\"version\":2}"));
        assertNull(cleared.location());
        assertEquals("변경", cleared.title());
        assertEquals(3, cleared.version());
        var past = service.update(event.id(), patch("{\"startsAt\":\"2020-01-01T10:00:00Z\",\"version\":3}"));
        assertEquals(OffsetDateTime.parse("2020-01-01T19:00:00+09:00"), past.startsAt());
        assertEquals(4, past.version());
    }

    @Test
    void staleRequestsDoNotChangeDataAndRepeatedPatchConflicts() {
        var event = create();
        var request = patch("{\"location\":\"새 장소\",\"version\":1}");
        service.update(event.id(), request);
        assertCode("VERSION_CONFLICT", () -> service.update(event.id(), request));
        assertCode("VERSION_CONFLICT", () -> service.delete(event.id(), 1));
        assertEquals("새 장소", jdbc.queryForObject("SELECT location FROM events WHERE id = ?", String.class, event.id()));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM schedule_items WHERE id = ?", Integer.class, event.id()));
    }

    @Test
    void deletionIsLogicalHiddenFromSharedViewAndRepeatedDeletionIsNotFound() {
        var event = create();
        service.delete(event.id(), 1);
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM events WHERE id = ?", OffsetDateTime.class, event.id()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM schedule_items WHERE id = ?", Integer.class, event.id()));
        assertTrue(service.list(null, null, null, 1, 100).items().stream().noneMatch(item -> item.id().equals(event.id())));
        assertCode("RESOURCE_NOT_FOUND", () -> service.delete(event.id(), 1));
        assertCode("RESOURCE_NOT_FOUND", () -> service.update(event.id(), patch("{\"location\":null,\"version\":1}")));
    }

    @Test
    void sessionsAndMissingEventsCannotBeMutated() {
        UUID session = UUID.randomUUID();
        jdbc.update("INSERT INTO study_sessions (id, title, starts_at) VALUES (?, '세션', now())", session);
        for (UUID id : new UUID[]{session, UUID.randomUUID()}) {
            assertCode("RESOURCE_NOT_FOUND", () -> service.update(id, patch("{\"location\":null,\"version\":1}")));
            assertCode("RESOURCE_NOT_FOUND", () -> service.delete(id, 1));
        }
        assertEquals("세션", jdbc.queryForObject("SELECT title FROM study_sessions WHERE id = ?", String.class, session));
        assertNull(jdbc.queryForObject("SELECT deleted_at FROM study_sessions WHERE id = ?", OffsetDateTime.class, session));
    }

    @Test
    void concurrentUpdatesHaveExactlyOneWinner() throws Exception {
        var event = create();
        var request = patch("{\"location\":\"변경\",\"version\":1}");
        var result = race(() -> service.update(event.id(), request), () -> service.update(event.id(), request));
        assertEquals(Set.of("OK", "VERSION_CONFLICT"), Set.of(result[0], result[1]));
        assertEquals(2, jdbc.queryForObject("SELECT version FROM events WHERE id = ?", Integer.class, event.id()));
    }

    @Test
    void concurrentUpdateAndDeleteCannotBothSucceed() throws Exception {
        var event = create();
        var result = race(() -> service.update(event.id(), patch("{\"location\":null,\"version\":1}")),
                () -> { service.delete(event.id(), 1); return null; });
        assertEquals(1, java.util.Arrays.stream(result).filter("OK"::equals).count());
        assertTrue(java.util.Arrays.stream(result).anyMatch(code -> Set.of("VERSION_CONFLICT", "RESOURCE_NOT_FOUND").contains(code)));
    }

    private String[] race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<String> a = () -> attempt(first, ready, start);
            Callable<String> b = () -> attempt(second, ready, start);
            var left = executor.submit(a);
            var right = executor.submit(b);
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return new String[]{left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS)};
        } finally {
            start.countDown();
        }
    }

    private String attempt(Callable<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); }
        try { action.call(); return "OK"; }
        catch (BusinessException e) { return e.getErrorCode().getCode(); }
    }

    private ScheduleResponse create() {
        Long memberId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', ?, 'hash', '관리자', 17) RETURNING id
                """, Long.class, UUID.randomUUID().toString());
        return service.create(new CreateScheduleRequest("회의", OffsetDateTime.parse("2026-10-02T19:00:00+09:00"), "학생회관"), memberId);
    }

    private UpdateScheduleRequest patch(String json) {
        return JsonMapper.builder().build().readValue(json, UpdateScheduleRequest.class);
    }

    private void assertCode(String expected, org.junit.jupiter.api.function.Executable action) {
        assertEquals(expected, assertThrows(BusinessException.class, action).getErrorCode().getCode());
    }
}
