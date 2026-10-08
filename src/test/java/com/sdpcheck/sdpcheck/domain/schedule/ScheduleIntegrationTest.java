package com.sdpcheck.sdpcheck.domain.schedule;

import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class ScheduleIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired ScheduleService service;
    @Autowired JdbcTemplate jdbc;

    @Test
    void mergesSessionsAndEventsBeforeFilteringOrderingAndPagination() {
        Long memberId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'schedule-admin', 'hash', '관리자', 17)
                RETURNING id
                """, Long.class);

        UUID sessionId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO study_sessions (id, title, starts_at, location)
                VALUES (?, '정기 세션', ?::timestamptz, NULL)
                """, sessionId, "2026-09-30T15:00:00Z");
        var created = service.create(new CreateScheduleRequest("운영진 회의",
                OffsetDateTime.parse("2026-10-02T19:00:00+09:00"), "학생회관"), memberId);
        var from = LocalDate.of(2026, 10, 1);
        var to = LocalDate.of(2026, 11, 1);

        var all = service.list(from, to, null, 1, 20);
        assertEquals(2, all.page().totalElements());
        assertEquals(sessionId, all.items().get(0).id());
        assertEquals("SESSION", all.items().get(0).type());
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                all.items().get(0).startsAt());
        assertFalse(all.items().get(0).canManage());
        assertNull(all.items().get(0).version());
        assertEquals(created.id(), all.items().get(1).id());
        assertTrue(all.items().get(1).canManage());
        assertEquals(1, all.items().get(1).version());

        var pageTwo = service.list(from, to, null, 2, 1);
        assertEquals(2, pageTwo.page().totalPages());
        assertEquals(created.id(), pageTwo.items().get(0).id());
        assertEquals(1, service.list(from, to, "EVENT", 1, 20).page().totalElements());
        assertEquals(0, service.list(LocalDate.of(2026, 9, 1), from,
                null, 1, 20).page().totalElements());
    }
}
