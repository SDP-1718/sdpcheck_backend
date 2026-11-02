package com.sdpcheck.sdpcheck.domain.schedule;

import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleQueryService;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.config.import=")
@Testcontainers
@ActiveProfiles("test")
@Transactional
class ScheduleQueryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-09T10:00:00Z");

    @Autowired ScheduleQueryService queries;
    @Autowired ScheduleService adminSchedules;
    @Autowired JdbcTemplate jdbc;

    private long authorId;

    @BeforeEach
    void createAuthor() {
        authorId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'schedule-query-admin', 'test-password-hash', '운영진', 17)
                RETURNING id
                """, Long.class);
    }

    @Test
    void emptySchedulesReturnAnEmptyPage() {
        var page = queries.getSchedules(0, 10);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.page());
        assertEquals(10, page.size());
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    void sortsTheMergedListBeforePaginationWithSessionsFirstAtTheSameTime() {
        insert(ScheduleType.GENERAL, uuid(1), STARTS_AT, "학생회관");
        insert(ScheduleType.GENERAL, uuid(2), STARTS_AT, null);
        insert(ScheduleType.SESSION, uuid(3), STARTS_AT, null);
        insert(ScheduleType.SESSION, uuid(4), STARTS_AT, "세미나실");
        insert(ScheduleType.GENERAL, uuid(5), STARTS_AT.minusDays(1), null);

        var first = queries.getSchedules(0, 2);
        var second = queries.getSchedules(1, 2);
        var third = queries.getSchedules(2, 2);

        assertEquals(List.of(uuid(5), uuid(3)), first.items().stream().map(ScheduleSummary::id).toList());
        assertEquals(List.of(uuid(4), uuid(1)), second.items().stream().map(ScheduleSummary::id).toList());
        assertEquals(List.of(uuid(2)), third.items().stream().map(ScheduleSummary::id).toList());
        assertEquals(ScheduleType.GENERAL, first.items().getFirst().type());
        assertEquals(ScheduleType.SESSION, first.items().get(1).type());
        assertEquals(5, first.totalElements());
        assertEquals(3, first.totalPages());
        assertTrue(first.hasNext());
        assertTrue(second.hasNext());
        assertFalse(third.hasNext());
        assertEquals(5, Stream.of(first, second, third).flatMap(page -> page.items().stream())
                .map(ScheduleSummary::id).distinct().count());
    }

    @Test
    void excludesDeletedPrivateAndCancelledItemsFromBothTypesAndTheCount() {
        int suffix = 1;
        for (ScheduleType type : ScheduleType.values()) {
            String table = table(type);
            for (String change : List.of("deleted_at = now()", "is_published = false", "cancelled_at = now()")) {
                UUID id = uuid(suffix++);
                insert(type, id, STARTS_AT, null);
                jdbc.update("UPDATE " + table + " SET " + change + " WHERE id = ?", id);
            }
        }

        var empty = queries.getSchedules(0, 10);
        assertTrue(empty.items().isEmpty());
        assertEquals(0, empty.totalElements());

        insert(ScheduleType.SESSION, uuid(10), STARTS_AT, null);
        insert(ScheduleType.GENERAL, uuid(11), STARTS_AT, null);
        var visible = queries.getSchedules(0, 10);

        assertEquals(List.of(uuid(10), uuid(11)), visible.items().stream().map(ScheduleSummary::id).toList());
        assertEquals(2, visible.totalElements());
        assertEquals(1, visible.totalPages());
    }

    @Test
    void includesPastAndFutureSchedulesWithoutChangingTheOriginalData() {
        insert(ScheduleType.GENERAL, uuid(1), OffsetDateTime.parse("2000-01-01T10:00:00Z"), null);
        insert(ScheduleType.SESSION, uuid(2), OffsetDateTime.parse("2099-01-01T10:00:00Z"), null);
        var before = jdbc.queryForMap("SELECT updated_at, version FROM events WHERE id = ?", uuid(1));

        var page = queries.getSchedules(0, 10);

        assertEquals(List.of(uuid(1), uuid(2)), page.items().stream().map(ScheduleSummary::id).toList());
        assertEquals(before, jdbc.queryForMap("SELECT updated_at, version FROM events WHERE id = ?", uuid(1)));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM study_sessions", Long.class));
    }

    @Test
    void returnsSeoulDatesAcrossMidnightAndPreservesNullLocation() {
        insert(ScheduleType.SESSION, uuid(1), OffsetDateTime.parse("2026-09-30T15:00:00Z"), null);

        var item = queries.getSchedules(0, 10).items().getFirst();

        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"), item.startsAt());
        assertEquals(ZoneOffset.ofHours(9), item.startsAt().getOffset());
        assertNull(item.location());
    }

    @Test
    void adminCreatedEventIsVisibleAsGeneralWithoutChangingTheAdminContract() {
        var created = adminSchedules.create(new CreateScheduleRequest("운영진 회의", STARTS_AT,
                "학생회관"), authorId);

        var item = queries.getSchedules(0, 10).items().getFirst();

        assertEquals("EVENT", created.type());
        assertEquals(created.id(), item.id());
        assertEquals(ScheduleType.GENERAL, item.type());
        assertEquals(created.title(), item.title());
        assertEquals(created.location(), item.location());
        assertEquals(created.startsAt(), item.startsAt());
    }

    @Test
    void retainsBothTypesWhenTheirIdsAreIdentical() {
        insert(ScheduleType.GENERAL, uuid(1), STARTS_AT, null);
        insert(ScheduleType.SESSION, uuid(1), STARTS_AT, null);

        var page = queries.getSchedules(0, 10);

        assertEquals(2, page.totalElements());
        assertEquals(List.of(ScheduleType.SESSION, ScheduleType.GENERAL),
                page.items().stream().map(ScheduleSummary::type).toList());
    }

    @Test
    void outOfRangePageIsEmptyButRetainsTotals() {
        insert(ScheduleType.SESSION, uuid(1), STARTS_AT, null);

        var page = queries.getSchedules(1, 10);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.page());
        assertEquals(1, page.totalElements());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    void largePageDoesNotOverflowTheSqlOffset() {
        insert(ScheduleType.GENERAL, uuid(1), STARTS_AT, null);

        var page = queries.getSchedules(Integer.MAX_VALUE, 100);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.totalElements());
        assertFalse(page.hasNext());
    }

    private void insert(ScheduleType type, UUID id, OffsetDateTime startsAt, String location) {
        if (type == ScheduleType.SESSION) {
            jdbc.update("""
                    INSERT INTO study_sessions (id, title, starts_at, location)
                    VALUES (?, '정기 세션', ?, ?)
                    """, id, startsAt, location);
        } else {
            jdbc.update("""
                    INSERT INTO events (id, title, starts_at, location, created_by)
                    VALUES (?, '일반 일정', ?, ?, ?)
                    """, id, startsAt, location, authorId);
        }
    }

    private static String table(ScheduleType type) {
        return type == ScheduleType.SESSION ? "study_sessions" : "events";
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
