package com.sdpcheck.sdpcheck.domain.notice;

import com.sdpcheck.sdpcheck.domain.file.exception.FileErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import com.sdpcheck.sdpcheck.domain.notice.exception.NoticeErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeQueryService;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = "spring.config.import=")
@Testcontainers
@ActiveProfiles("test")
@Transactional
class NoticeQueryIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-09T01:00:00Z");
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    @Autowired NoticeQueryService queries;
    @Autowired NoticeService notices;
    @Autowired NoticeImageService images;
    @Autowired JdbcTemplate jdbc;

    private long authorId;

    @BeforeEach
    void createAuthor() {
        authorId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'notice-query-admin', 'test-password-hash', '운영진', 17)
                RETURNING id
                """, Long.class);
    }

    @Test
    void emptyListReturnsEmptyPage() {
        var page = queries.getNotices(0, 10);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    void pinnedNoticeOccupiesOneSlotWithoutRepeatingAcrossPages() {
        UUID pinned = uuid(1);
        UUID older = uuid(2);
        UUID sameTimeLowerId = uuid(3);
        UUID sameTimeHigherId = uuid(4);
        insertNotice(pinned, true, true, false, CREATED_AT.minusDays(2));
        insertNotice(older, false, true, false, CREATED_AT.minusDays(1));
        insertNotice(sameTimeLowerId, false, true, false, CREATED_AT);
        insertNotice(sameTimeHigherId, false, true, false, CREATED_AT);
        insertNotice(uuid(5), false, false, false, CREATED_AT.plusDays(1));
        insertNotice(uuid(6), false, true, true, CREATED_AT.plusDays(2));

        var first = queries.getNotices(0, 2);
        var second = queries.getNotices(1, 2);

        assertEquals(List.of(pinned, sameTimeHigherId), first.items().stream().map(NoticeSummary::id).toList());
        assertEquals(List.of(sameTimeLowerId, older), second.items().stream().map(NoticeSummary::id).toList());
        assertEquals(4, first.totalElements());
        assertEquals(4, second.totalElements());
        assertEquals(2, first.totalPages());
        assertTrue(first.hasNext());
        assertFalse(second.hasNext());
        assertTrue(first.items().getFirst().isPinned());
        assertEquals(ZoneOffset.ofHours(9), first.items().getFirst().createdAt().getOffset());
    }

    @Test
    void onlyHiddenOrDeletedNoticesProduceAnEmptyPage() {
        insertNotice(uuid(1), false, false, false, CREATED_AT);
        insertNotice(uuid(2), false, true, true, CREATED_AT);

        var page = queries.getNotices(0, 10);

        assertTrue(page.items().isEmpty());
        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
    }

    @Test
    void pagePastTheEndRetainsTotalCount() {
        insertNotice(uuid(1), false, true, false, CREATED_AT);

        var page = queries.getNotices(1, 10);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.page());
        assertEquals(1, page.totalElements());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasNext());
    }

    @Test
    void largePageNumberDoesNotOverflowSqlOffset() {
        insertNotice(uuid(1), false, true, false, CREATED_AT);

        var page = queries.getNotices(Integer.MAX_VALUE, 100);

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.totalElements());
        assertFalse(page.hasNext());
    }

    @Test
    void detailPreservesPlainTextAndReturnsSeoulTimestampsWithoutWriting() {
        UUID noticeId = uuid(1);
        insertNotice(noticeId, true, true, false, CREATED_AT);
        OffsetDateTime before = jdbc.queryForObject(
                "SELECT updated_at FROM notices WHERE id = ?", OffsetDateTime.class, noticeId);

        var detail = queries.getNotice(noticeId);

        assertEquals(noticeId, detail.id());
        assertEquals("공지 제목", detail.title());
        assertEquals("첫 번째 줄\n두 번째 줄", detail.content());
        assertTrue(detail.isPinned());
        assertEquals(Long.toString(authorId), detail.author().id());
        assertEquals("운영진", detail.author().name());
        assertTrue(detail.imageUrls().isEmpty());
        assertEquals(OffsetDateTime.parse("2026-10-09T10:00:00+09:00"), detail.createdAt());
        assertEquals(detail.createdAt(), detail.updatedAt());
        assertEquals(before, jdbc.queryForObject(
                "SELECT updated_at FROM notices WHERE id = ?", OffsetDateTime.class, noticeId));
    }

    @Test
    void missingHiddenAndDeletedNoticesHaveTheSameNotFoundError() {
        UUID hidden = uuid(1);
        UUID deleted = uuid(2);
        UUID missing = uuid(3);
        insertNotice(hidden, false, false, false, CREATED_AT);
        insertNotice(deleted, false, true, true, CREATED_AT);

        for (UUID id : List.of(hidden, deleted, missing)) {
            BusinessException exception = assertThrows(BusinessException.class, () -> queries.getNotice(id));
            assertEquals(NoticeErrorCode.NOTICE_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Test
    void adminCreatedNoticeIsVisibleAndImagesFollowAttachmentOrder() {
        var firstImage = images.upload(image("first.png"), authorId);
        var secondImage = images.upload(image("second.png"), authorId);
        images.upload(image("unattached.png"), authorId);
        var created = notices.create(new CreateNoticeRequest("이미지 공지", "본문", false,
                List.of(secondImage.fileId(), firstImage.fileId())), authorId);

        var detail = queries.getNotice(created.id());

        assertEquals(List.of("/api/v1/files/" + secondImage.fileId(),
                "/api/v1/files/" + firstImage.fileId()), detail.imageUrls());
        assertEquals(1, queries.getNotices(0, 10).totalElements());
        assertEquals(created.author(), detail.author());
    }

    @Test
    void imageCannotBeDownloadedAfterItsNoticeBecomesPrivate() {
        var uploaded = images.upload(image("private.png"), authorId);
        var created = notices.create(new CreateNoticeRequest("비공개 전환", "본문", false,
                List.of(uploaded.fileId())), authorId);
        jdbc.update("UPDATE notices SET is_published = false WHERE id = ?", created.id());

        BusinessException exception = assertThrows(BusinessException.class, () -> images.get(uploaded.fileId()));

        assertEquals(FileErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
        assertTrue(queries.getNotices(0, 10).items().isEmpty());
    }

    @Test
    void imageCannotBeDownloadedAfterItsNoticeIsDeleted() {
        var uploaded = images.upload(image("deleted.png"), authorId);
        var created = notices.create(new CreateNoticeRequest("삭제 공지", "본문", false,
                List.of(uploaded.fileId())), authorId);
        jdbc.update("UPDATE notices SET deleted_at = now() WHERE id = ?", created.id());

        BusinessException exception = assertThrows(BusinessException.class, () -> images.get(uploaded.fileId()));

        assertEquals(FileErrorCode.FILE_NOT_FOUND, exception.getErrorCode());
    }

    private void insertNotice(UUID id, boolean pinned, boolean published, boolean deleted, OffsetDateTime createdAt) {
        jdbc.update("""
                INSERT INTO notices
                    (id, title, content, is_pinned, is_published, author_id, created_at, updated_at, deleted_at)
                VALUES (?, '공지 제목', ?, ?, ?, ?, ?, ?, ?)
                """, id, "첫 번째 줄\n두 번째 줄", pinned, published, authorId, createdAt, createdAt,
                deleted ? createdAt : null);
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }

    private static MockMultipartFile image(String name) {
        return new MockMultipartFile("file", name, "image/png", PNG);
    }
}
