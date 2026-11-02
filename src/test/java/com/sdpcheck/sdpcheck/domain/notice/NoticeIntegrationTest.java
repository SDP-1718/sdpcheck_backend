package com.sdpcheck.sdpcheck.domain.notice;

import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@Transactional
class NoticeIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired NoticeService service;
    @Autowired NoticeImageService images;
    @Autowired JdbcTemplate jdbc;

    @Test
    void creatingAnotherPinnedNoticeUnpinsPreviousWithoutDuplicatingList() {
        Long memberId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'notice-admin', 'hash', '관리자', 17)
                RETURNING id
                """, Long.class);
        var first = service.create(new CreateNoticeRequest("첫 공지", "본문", true, List.of()), memberId);
        var second = service.create(new CreateNoticeRequest("두 번째 공지", "본문", true, List.of()), memberId);

        var result = service.list();
        assertEquals(second.id(), result.pinnedNotice().id());
        assertEquals(1, result.items().size());
        assertEquals(first.id(), result.items().getFirst().id());
        assertFalse(result.items().getFirst().isPinned());
        assertTrue(result.pinnedNotice().isPinned());
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM notices WHERE is_pinned = true AND deleted_at IS NULL
                """, Integer.class));
    }

    @Test
    void uploadedImageIsPrivateUntilItIsAttachedToNotice() {
        Long memberId = jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', 'image-admin', 'hash', '관리자', 17)
                RETURNING id
                """, Long.class);
        byte[] png = { (byte) 137, 80, 78, 71, 13, 10, 26, 10 };
        var uploaded = images.upload(new MockMultipartFile("file", "sample.png", "image/png", png), memberId);
        assertThrows(BusinessException.class, () -> images.get(uploaded.fileId()));

        var notice = service.create(new CreateNoticeRequest("이미지 공지", "본문", false,
                List.of(uploaded.fileId())), memberId);
        assertEquals(uploaded.fileId(), notice.images().getFirst().fileId());
        assertEquals("/api/v1/files/" + uploaded.fileId(), notice.images().getFirst().url());
        assertNull(notice.images().getFirst().expiresAt());
        assertArrayEquals(png, images.get(uploaded.fileId()).bytes());
    }
}
