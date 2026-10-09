package com.sdpcheck.sdpcheck.domain.notice.repository;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDetailResponse;
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
public class NoticeQueryRepository {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final NamedParameterJdbcTemplate jdbc;

    public NoticeQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long countVisible() {
        Long count = jdbc.getJdbcTemplate().queryForObject("""
                SELECT count(*) FROM notices
                WHERE deleted_at IS NULL AND is_published = true
                """, Long.class);
        return count == null ? 0 : count;
    }

    public List<NoticeSummary> findVisiblePage(int page, int size) {
        var params = new MapSqlParameterSource()
                .addValue("limit", size)
                .addValue("offset", (long) page * size);
        return jdbc.query("""
                SELECT n.id, n.title, n.is_pinned, n.created_at, n.updated_at,
                       n.author_id, m.name AS author_name
                FROM notices n JOIN members m ON m.id = n.author_id
                WHERE n.deleted_at IS NULL AND n.is_published = true
                ORDER BY n.is_pinned DESC, n.created_at DESC, n.id DESC
                LIMIT :limit OFFSET :offset
                """, params, (rs, rowNum) -> new NoticeSummary(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getBoolean("is_pinned"),
                new NoticeAuthor(Long.toString(rs.getLong("author_id")), rs.getString("author_name")),
                seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class))));
    }

    public Optional<NoticeDetailResponse> findVisibleById(UUID noticeId) {
        return jdbc.query("""
                SELECT n.id, n.title, n.content, n.is_pinned, n.created_at, n.updated_at,
                       n.author_id, m.name AS author_name
                FROM notices n JOIN members m ON m.id = n.author_id
                WHERE n.id = :id AND n.deleted_at IS NULL AND n.is_published = true
                """, new MapSqlParameterSource("id", noticeId), (rs, rowNum) -> new NoticeDetailResponse(
                rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("content"),
                rs.getBoolean("is_pinned"),
                new NoticeAuthor(Long.toString(rs.getLong("author_id")), rs.getString("author_name")),
                List.of(), seoul(rs.getObject("created_at", OffsetDateTime.class)),
                seoul(rs.getObject("updated_at", OffsetDateTime.class))))
                .stream().findFirst();
    }

    public List<String> findImageUrls(UUID noticeId) {
        return jdbc.query("""
                SELECT ni.file_id
                FROM notice_images ni
                JOIN uploaded_files f ON f.id = ni.file_id
                JOIN notices n ON n.id = ni.notice_id
                WHERE ni.notice_id = :id AND f.status = 'ATTACHED'
                      AND n.deleted_at IS NULL AND n.is_published = true
                ORDER BY ni.position ASC
                """, new MapSqlParameterSource("id", noticeId),
                (rs, rowNum) -> "/api/v1/files/" + rs.getObject("file_id", UUID.class));
    }

    private static OffsetDateTime seoul(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(SEOUL).toOffsetDateTime();
    }
}
