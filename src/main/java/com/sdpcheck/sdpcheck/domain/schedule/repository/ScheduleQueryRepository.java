package com.sdpcheck.sdpcheck.domain.schedule.repository;

import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Repository
public class ScheduleQueryRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String VISIBLE_SCHEDULES = """
            SELECT id, 'SESSION'::text AS type, 0 AS type_order, title, starts_at, location
            FROM study_sessions
            WHERE deleted_at IS NULL AND is_published = true AND cancelled_at IS NULL
            UNION ALL
            SELECT id, 'GENERAL'::text AS type, 1 AS type_order, title, starts_at, location
            FROM events
            WHERE deleted_at IS NULL AND is_published = true AND cancelled_at IS NULL
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public ScheduleQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countVisible() {
        String sql = "SELECT count(*) FROM (%s) visible_schedules".formatted(VISIBLE_SCHEDULES);
        Long count = jdbc.getJdbcTemplate().queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    public List<ScheduleSummary> findVisiblePage(int page, int size) {
        String sql = """
                SELECT id, type, title, starts_at, location
                FROM (%s) visible_schedules
                ORDER BY starts_at ASC, type_order ASC, id ASC
                LIMIT :limit OFFSET :offset
                """.formatted(VISIBLE_SCHEDULES);
        var params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        return jdbc.query(sql, params, (rs, rowNum) -> new ScheduleSummary(
                rs.getObject("id", UUID.class), ScheduleType.valueOf(rs.getString("type")),
                rs.getString("title"), rs.getObject("starts_at", OffsetDateTime.class)
                        .atZoneSameInstant(SEOUL).toOffsetDateTime(), rs.getString("location")));
    }
}
