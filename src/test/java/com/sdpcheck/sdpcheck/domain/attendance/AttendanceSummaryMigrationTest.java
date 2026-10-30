package com.sdpcheck.sdpcheck.domain.attendance;

import com.sdpcheck.sdpcheck.domain.attendance.repository.AttendanceQueryRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
class AttendanceSummaryMigrationTest {
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Test
    void preservesExistingSemestersAndRequiresExplicitUniqueNonblankPeriodCodes() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var jdbc = new JdbcTemplate(dataSource);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("8").load().migrate();

        UUID current = UUID.randomUUID();
        UUID previous = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on, is_current)
                VALUES (?, '2026년 2학기', DATE '2026-07-01', DATE '2026-12-31', true),
                       (?, '이전 학기', DATE '2026-01-01', DATE '2026-06-30', false)
                """, current, previous);

        Flyway latest = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();
        latest.migrate();
        latest.validate();

        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM semesters", Long.class));
        assertEquals("2026년 2학기", jdbc.queryForObject(
                "SELECT name FROM semesters WHERE id = ?", String.class, current));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT is_current FROM semesters WHERE id = ?", Boolean.class, current));
        assertNull(jdbc.queryForObject("SELECT period_code FROM semesters WHERE id = ?", String.class, current));
        assertNull(jdbc.queryForObject("SELECT period_code FROM semesters WHERE id = ?", String.class, previous));

        var repository = new AttendanceQueryRepository(new NamedParameterJdbcTemplate(dataSource));
        assertEquals(current, repository.findCurrentSemesterId().orElseThrow());

        jdbc.update("UPDATE semesters SET period_code = '2026-2H' WHERE id = ?", current);
        assertEquals("2026-2H", repository.findCurrentSemester().orElseThrow().periodCode());

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE semesters SET period_code = '2026-2H' WHERE id = ?", previous));
        for (String invalid : new String[] {"", " ", " 2026-1H", "2026-1H "}) {
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE semesters SET period_code = ? WHERE id = ?", invalid, previous));
        }
        jdbc.update("UPDATE semesters SET period_code = '2026-1H' WHERE id = ?", previous);
        assertEquals("2026-1H", jdbc.queryForObject(
                "SELECT period_code FROM semesters WHERE id = ?", String.class, previous));
    }
}
