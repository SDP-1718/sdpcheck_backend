package com.sdpcheck.sdpcheck.domain.notice;

import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.request.UpdateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeQueryService;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class NoticeMutationIntegrationTest {
    @Container @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
    @Autowired NoticeService service;
    @Autowired NoticeQueryService queries;
    @Autowired NoticeImageService images;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void partialUpdatePreservesAuthorCreationTimeAndOmittedFields() {
        long author = admin();
        UUID file = upload(author);
        var notice = create(author, false, List.of(file));
        var updated = service.update(notice.id(), patch("{\"title\":\"  변경 제목  \"}"), admin());
        assertEquals("변경 제목", updated.title());
        assertEquals(notice.content(), updated.content());
        assertEquals(notice.author(), updated.author());
        assertEquals(notice.createdAt(), updated.createdAt());
        assertTrue(updated.updatedAt().isAfter(notice.updatedAt()));
        assertFalse(updated.isPinned());
        assertEquals(notice.images(), updated.images());
        assertEquals("변경 제목", queries.getNotice(notice.id()).title());

        var unpinned = service.update(notice.id(), patch("{\"isPinned\":true}"), author);
        assertTrue(unpinned.isPinned());
        assertFalse(service.update(notice.id(), patch("{\"isPinned\":false}"), author).isPinned());
    }

    @Test
    void anotherAdminCanReorderExistingImagesAddOwnUploadAndRemoveAll() {
        long author = admin();
        long editor = admin();
        UUID first = upload(author);
        UUID second = upload(author);
        UUID added = upload(editor);
        var notice = create(author, false, List.of(first, second));
        jdbc.update("UPDATE uploaded_files SET attach_before = now() - interval '1 day' WHERE id IN (?, ?)", first, second);

        var reordered = service.update(notice.id(), imagePatch(second, first, added), editor);
        assertEquals(List.of(second, first, added), reordered.images().stream().map(image -> image.fileId()).toList());
        assertEquals(List.of("/api/v1/files/" + second, "/api/v1/files/" + first, "/api/v1/files/" + added),
                queries.getNotice(notice.id()).imageUrls());
        assertTrue(reordered.images().stream().allMatch(image -> image.expiresAt() == null));

        service.update(notice.id(), imagePatch(second), editor);
        assertCode("FILE_NOT_FOUND", () -> images.get(first));
        assertCode("FILE_NOT_FOUND", () -> images.get(added));
        assertNotNull(images.get(second));
        assertTrue(service.update(notice.id(), imagePatch(), editor).images().isEmpty());
        assertTrue(queries.getNotice(notice.id()).imageUrls().isEmpty());
        assertCode("FILE_NOT_FOUND", () -> images.get(second));
        // Removed uploads cannot be reused as a new PENDING upload.
        assertCode("FILE_NOT_READY", () -> service.update(notice.id(), imagePatch(added), editor));
    }

    @Test
    void invalidFileRollsBackPinContentAndImageChanges() {
        long author = admin();
        var pinned = create(author, true, List.of());
        UUID original = upload(author);
        var notice = create(author, false, List.of(original));
        UUID pending = upload(author);
        String body = "{\"title\":\"실패할 제목\",\"isPinned\":true,\"imageIds\":[\""
                + pending + "\",\"" + UUID.randomUUID() + "\"]}";
        assertCode("FILE_NOT_FOUND", () -> service.update(notice.id(), patch(body), author));
        assertEquals(notice.title(), queries.getNotice(notice.id()).title());
        assertTrue(queries.getNotice(pinned.id()).isPinned());
        assertFalse(queries.getNotice(notice.id()).isPinned());
        assertEquals(List.of("/api/v1/files/" + original), queries.getNotice(notice.id()).imageUrls());
        assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM uploaded_files WHERE id = ?", String.class, pending));
    }

    @Test
    void rejectsForeignExpiredAlreadyAttachedAndInvalidImages() {
        long author = admin();
        var notice = create(author, false, List.of());
        UUID foreign = upload(admin());
        assertCode("FILE_NOT_FOUND", () -> service.update(notice.id(), imagePatch(foreign), author));
        UUID expired = upload(author);
        jdbc.update("UPDATE uploaded_files SET attach_before = now() - interval '1 second' WHERE id = ?", expired);
        assertCode("FILE_NOT_READY", () -> service.update(notice.id(), imagePatch(expired), author));
        UUID attached = upload(author);
        create(author, false, List.of(attached));
        assertCode("FILE_NOT_READY", () -> service.update(notice.id(), imagePatch(attached), author));
        UUID invalid = upload(author);
        jdbc.update("UPDATE uploaded_files SET data = ? WHERE id = ?", new byte[8], invalid);
        assertCode("INVALID_IMAGE", () -> service.update(notice.id(), imagePatch(invalid), author));
        assertTrue(queries.getNotice(notice.id()).imageUrls().isEmpty());
    }

    @Test
    void logicalDeletionUnpinsAndHidesNoticeAndImagesFromAllReads() {
        long author = admin();
        UUID file = upload(author);
        var notice = create(author, true, List.of(file));
        service.delete(notice.id());
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM notices WHERE id = ?", OffsetDateTime.class, notice.id()));
        assertFalse(jdbc.queryForObject("SELECT is_pinned FROM notices WHERE id = ?", Boolean.class, notice.id()));
        var adminList = service.list();
        assertNull(adminList.pinnedNotice());
        assertTrue(adminList.items().stream().noneMatch(item -> item.id().equals(notice.id())));
        assertTrue(queries.getNotices(0, 100).items().stream().noneMatch(item -> item.id().equals(notice.id())));
        assertCode("NOTICE_NOT_FOUND", () -> queries.getNotice(notice.id()));
        assertCode("FILE_NOT_FOUND", () -> images.get(file));
        assertCode("RESOURCE_NOT_FOUND", () -> service.delete(notice.id()));
        assertCode("RESOURCE_NOT_FOUND", () -> service.update(notice.id(), imagePatch(), author));
        assertCode("RESOURCE_NOT_FOUND", () -> service.delete(UUID.randomUUID()));
        assertCode("RESOURCE_NOT_FOUND", () -> service.update(UUID.randomUUID(), imagePatch(), author));
    }

    @Test
    void concurrentPartialUpdatesPreserveEachOthersOmittedFields() throws Exception {
        long author = admin();
        var notice = create(author, false, List.of());
        var result = race(() -> service.update(notice.id(), patch("{\"title\":\"새 제목\"}"), author),
                () -> service.update(notice.id(), patch("{\"content\":\"새 본문\"}"), author));
        assertEquals(List.of("OK", "OK"), result);
        var finalNotice = queries.getNotice(notice.id());
        assertEquals("새 제목", finalNotice.title());
        assertEquals("새 본문", finalNotice.content());
    }

    @Test
    void concurrentPinUpdatesAndPinnedCreationKeepSinglePin() throws Exception {
        long author = admin();
        var first = create(author, false, List.of());
        var second = create(author, false, List.of());
        assertEquals(List.of("OK", "OK"), race(
                () -> service.update(first.id(), patch("{\"isPinned\":true}"), author),
                () -> service.update(second.id(), patch("{\"isPinned\":true}"), author)));
        assertSinglePin();
        assertEquals(List.of("OK", "OK"), race(
                () -> service.update(first.id(), patch("{\"isPinned\":true}"), author),
                () -> create(author, true, List.of())));
        assertSinglePin();
    }

    @Test
    void concurrentUpdateAndDeletionNeverRestoreDeletedNotice() throws Exception {
        long author = admin();
        var notice = create(author, true, List.of());
        var result = race(() -> service.update(notice.id(), patch("{\"isPinned\":true}"), author),
                () -> { service.delete(notice.id()); return null; });
        assertTrue(List.of("OK", "RESOURCE_NOT_FOUND").contains(result.getFirst()));
        assertEquals("OK", result.getLast());
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM notices WHERE id = ?", OffsetDateTime.class, notice.id()));
        assertFalse(jdbc.queryForObject("SELECT is_pinned FROM notices WHERE id = ?", Boolean.class, notice.id()));
    }

    @Test
    void concurrentReuseOfUploadOnlyAttachesItOnce() throws Exception {
        long author = admin();
        UUID pending = upload(author);
        var first = create(author, false, List.of());
        var second = create(author, false, List.of());
        var results = race(() -> service.update(first.id(), imagePatch(pending), author),
                () -> service.update(second.id(), imagePatch(pending), author));
        assertEquals(1, results.stream().filter("OK"::equals).count());
        assertEquals(1, results.stream().filter("FILE_NOT_READY"::equals).count());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notice_images WHERE file_id = ?", Integer.class, pending));
    }

    @Test
    void swaggerDocumentsPartialFieldsOperationIdsAndEmptyDeleteResponse() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/{noticeId}'].patch.operationId").value("updateNotice"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/{noticeId}'].delete.operationId").value("deleteNotice"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/notices/{noticeId}'].delete.responses['204'].content").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.title.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.content.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.isPinned.type").value("boolean"))
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.imageIds.type").value("array"))
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.imageIds.items.format").value("uuid"))
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.changePresent").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.UpdateNoticeRequest.properties.pinned").doesNotExist());
    }

    private long admin() {
        return jdbc.queryForObject("""
                INSERT INTO members (role, login_id, password, name, generation)
                VALUES ('ADMIN', ?, 'hash', '관리자', 17) RETURNING id
                """, Long.class, "n-" + UUID.randomUUID());
    }

    private NoticeResponse create(long author, boolean pinned, List<UUID> ids) {
        return service.create(new CreateNoticeRequest("원래 제목", "원래 본문", pinned, ids), author);
    }

    private UUID upload(long owner) {
        return images.upload(new MockMultipartFile("file", "image.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10}), owner).fileId();
    }

    private UpdateNoticeRequest patch(String body) {
        return mapper.readValue(body, UpdateNoticeRequest.class);
    }

    private UpdateNoticeRequest imagePatch(UUID... ids) {
        return patch("{\"imageIds\":" + mapper.writeValueAsString(List.of(ids)) + "}");
    }

    private void assertSinglePin() {
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notices WHERE is_pinned AND deleted_at IS NULL", Integer.class));
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable action) {
        assertEquals(code, assertThrows(BusinessException.class, action).getErrorCode().getCode());
    }

    private List<String> race(Callable<?> first, Callable<?> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> attempt(first, ready, start));
            var right = executor.submit(() -> attempt(second, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            return List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        } finally {
            start.countDown();
        }
    }

    private String attempt(Callable<?> action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) { throw new IllegalStateException("start timeout"); }
        try {
            action.call();
            return "OK";
        } catch (BusinessException exception) {
            return exception.getErrorCode().getCode();
        }
    }
}
