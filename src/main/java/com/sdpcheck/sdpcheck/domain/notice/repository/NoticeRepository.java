package com.sdpcheck.sdpcheck.domain.notice.repository;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NoticeRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final NamedParameterJdbcTemplate jdbc;

    public NoticeRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void lockPinSlot() {
        jdbc.getJdbcTemplate().execute("SELECT pg_advisory_xact_lock(27092701)");
    }

    public void unpinCurrent() {
        jdbc.getJdbcTemplate().update("""
                UPDATE notices SET is_pinned = false, updated_at = now()
                WHERE is_pinned = true AND deleted_at IS NULL
                """);
    }

    public NoticeResponse create(UUID id, String title, String content, boolean pinned, long authorId) {
        var params = new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("title", title)
                .addValue("content", content)
                .addValue("pinned", pinned)
                .addValue("authorId", authorId);
        return jdbc.queryForObject("""
                WITH created AS (
                    INSERT INTO notices (id, title, content, is_pinned, author_id)
                    VALUES (:id, :title, :content, :pinned, :authorId)
                    RETURNING id, title, content, is_pinned, author_id, created_at, updated_at
                )
                SELECT created.*, members.name AS author_name
                FROM created JOIN members ON members.id = created.author_id
                """, params, (rs, rowNum) -> new NoticeResponse(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("content"),
                rs.getBoolean("is_pinned"), new NoticeAuthor(
                Long.toString(rs.getLong("author_id")), rs.getString("author_name")), List.of(),
                seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class))));
    }

    public Optional<NoticeResponse> findActiveForUpdate(UUID noticeId) {
        return jdbc.query("""
                SELECT n.*, m.name AS author_name
                FROM notices n JOIN members m ON m.id = n.author_id
                WHERE n.id = :id AND n.deleted_at IS NULL
                FOR UPDATE OF n
                """, new MapSqlParameterSource("id", noticeId), this::mapNotice)
                .stream().findFirst();
    }

    public NoticeResponse update(UUID noticeId, String title, String content, boolean pinned) {
        var params = new MapSqlParameterSource("id", noticeId)
                .addValue("title", title).addValue("content", content).addValue("pinned", pinned);
        return jdbc.queryForObject("""
                WITH updated AS (
                    UPDATE notices
                    SET title = :title, content = :content, is_pinned = :pinned,
                        updated_at = clock_timestamp()
                    WHERE id = :id AND deleted_at IS NULL
                    RETURNING *
                )
                SELECT updated.*, m.name AS author_name
                FROM updated JOIN members m ON m.id = updated.author_id
                """, params, this::mapNotice);
    }

    public void softDelete(UUID noticeId) {
        jdbc.update("""
                UPDATE notices SET deleted_at = clock_timestamp(), updated_at = clock_timestamp(),
                                   is_pinned = false
                WHERE id = :id AND deleted_at IS NULL
                """, new MapSqlParameterSource("id", noticeId));
    }

    private NoticeResponse mapNotice(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new NoticeResponse(rs.getObject("id", UUID.class), rs.getString("title"),
                rs.getString("content"), rs.getBoolean("is_pinned"),
                new NoticeAuthor(Long.toString(rs.getLong("author_id")), rs.getString("author_name")),
                List.of(), seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class)));
    }

    public List<NoticeSummary> list() {
        return jdbc.getJdbcTemplate().query("""
                SELECT n.id, n.title, n.is_pinned, n.created_at, n.updated_at,
                       n.author_id, m.name AS author_name
                FROM notices n JOIN members m ON m.id = n.author_id
                WHERE n.deleted_at IS NULL
                ORDER BY n.is_pinned DESC, n.created_at DESC, n.id DESC
                """, (rs, rowNum) -> new NoticeSummary(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getBoolean("is_pinned"),
                new NoticeAuthor(Long.toString(rs.getLong("author_id")), rs.getString("author_name")),
                seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class))));
    }

    private static OffsetDateTime seoul(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
