package com.sdpcheck.sdpcheck.domain.schedule.repository;

import com.sdpcheck.sdpcheck.domain.schedule.dto.request.UpdateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleItem;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ScheduleRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final NamedParameterJdbcTemplate jdbc;

    public ScheduleRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ScheduleResponse create(UUID id, String title, OffsetDateTime startsAt,
                                   String location, long memberId) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("title", title)
                .addValue("startsAt", startsAt)
                .addValue("location", location)
                .addValue("memberId", memberId);
        return jdbc.queryForObject("""
                INSERT INTO events (id, title, starts_at, location, created_by)
                VALUES (:id, :title, :startsAt, :location, :memberId)
                RETURNING id, title, starts_at, location, created_at, updated_at, version
                """, params, (rs, rowNum) -> new ScheduleResponse(
                rs.getObject("id", UUID.class), "EVENT", rs.getString("title"),
                seoul(rs.getObject("starts_at", OffsetDateTime.class)), rs.getString("location"),
                seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class)), rs.getInt("version")));
    }

    // Both mutations lock the same active event row until the service transaction commits.
    public Optional<ScheduleResponse> findActiveForUpdate(UUID id) {
        return jdbc.query("""
                SELECT id, title, starts_at, location, created_at, updated_at, version
                FROM events WHERE id = :id AND deleted_at IS NULL
                FOR UPDATE
                """, new MapSqlParameterSource("id", id), eventMapper()).stream().findFirst();
    }

    public ScheduleResponse update(UUID id, UpdateScheduleRequest request) {
        var params = new MapSqlParameterSource("id", id)
                .addValue("version", request.version())
                .addValue("titlePresent", request.titlePresent()).addValue("title", request.title())
                .addValue("startsAtPresent", request.startsAtPresent()).addValue("startsAt", request.startsAt())
                .addValue("locationPresent", request.locationPresent()).addValue("location", request.location());
        return jdbc.queryForObject("""
                UPDATE events SET
                    title = CASE WHEN :titlePresent THEN CAST(:title AS varchar) ELSE title END,
                    starts_at = CASE WHEN :startsAtPresent THEN CAST(:startsAt AS timestamptz) ELSE starts_at END,
                    location = CASE WHEN :locationPresent THEN CAST(:location AS varchar) ELSE location END,
                    updated_at = clock_timestamp(), version = version + 1
                WHERE id = :id AND version = :version AND deleted_at IS NULL
                RETURNING id, title, starts_at, location, created_at, updated_at, version
                """, params, eventMapper());
    }

    public void delete(UUID id, int version) {
        int changed = jdbc.update("""
                UPDATE events SET deleted_at = clock_timestamp(), updated_at = clock_timestamp(),
                    version = version + 1
                WHERE id = :id AND version = :version AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", id).addValue("version", version));
        if (changed != 1) {
            throw new IllegalStateException("잠근 일반 일정의 삭제에 실패했습니다.");
        }
    }

    private static RowMapper<ScheduleResponse> eventMapper() {
        return (rs, rowNum) -> new ScheduleResponse(
                rs.getObject("id", UUID.class), "EVENT", rs.getString("title"),
                seoul(rs.getObject("starts_at", OffsetDateTime.class)), rs.getString("location"),
                seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class)), rs.getInt("version"));
    }

    public long count(OffsetDateTime from, OffsetDateTime to, String type) {
        var query = filter(from, to, type);
        return jdbc.queryForObject("SELECT count(*) FROM schedule_items WHERE " + query.where(),
                query.params(), Long.class);
    }

    public List<ScheduleItem> find(OffsetDateTime from, OffsetDateTime to, String type,
                                   int page, int size) {
        var query = filter(from, to, type);
        query.params().addValue("size", size).addValue("offset", (long) (page - 1) * size);
        String sql = """
                SELECT id, type, title, starts_at, location, version
                FROM schedule_items
                WHERE %s
                ORDER BY starts_at ASC, type ASC, id ASC
                LIMIT :size OFFSET :offset
                """.formatted(query.where());
        return jdbc.query(sql, query.params(), (rs, rowNum) -> {
            String itemType = rs.getString("type");
            Integer version = rs.getObject("version", Integer.class);
            return new ScheduleItem(rs.getObject("id", UUID.class), itemType, rs.getString("title"),
                    seoul(rs.getObject("starts_at", OffsetDateTime.class)), rs.getString("location"),
                    "EVENT".equals(itemType), version);
        });
    }

    private Filter filter(OffsetDateTime from, OffsetDateTime to, String type) {
        StringBuilder where = new StringBuilder("1=1");
        var params = new MapSqlParameterSource();
        if (from != null) {
            where.append(" AND starts_at >= :from");
            params.addValue("from", from);
        }
        if (to != null) {
            where.append(" AND starts_at < :to");
            params.addValue("to", to);
        }
        if (type != null) {
            where.append(" AND type = :type");
            params.addValue("type", type);
        }
        return new Filter(where.toString(), params);
    }

    private static OffsetDateTime seoul(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }

    private record Filter(String where, MapSqlParameterSource params) {
    }
}
