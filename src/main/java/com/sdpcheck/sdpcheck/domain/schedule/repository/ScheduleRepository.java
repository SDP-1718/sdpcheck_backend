package com.sdpcheck.sdpcheck.domain.schedule.repository;

import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleItem;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

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

    public long count(OffsetDateTime from, OffsetDateTime to, String type) {
        var query = filter(from, to, type);
        return jdbc.queryForObject("SELECT count(*) FROM schedule_items WHERE " + query.where(),
                query.params(), Long.class);
    }

    public List<ScheduleItem> find(OffsetDateTime from, OffsetDateTime to, String type,
                                   int page, int size) {
        var query = filter(from, to, type);
        query.params().addValue("size", size).addValue("offset", (long) (page - 1) * size);
        return jdbc.query("""
                SELECT id, type, title, starts_at, location, version
                FROM schedule_items WHERE """ + query.where() + """
                ORDER BY starts_at ASC, type ASC, id ASC
                LIMIT :size OFFSET :offset
                """, query.params(), (rs, rowNum) -> {
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
