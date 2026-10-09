package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import com.sdpcheck.sdpcheck.domain.notice.repository.NoticeRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoticeServiceTest {
    private final NoticeRepository repository = mock(NoticeRepository.class);
    private final NoticeImageService images = mock(NoticeImageService.class);
    private final NoticeService service = new NoticeService(repository, images);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-08T12:00:00+09:00");

    @Test
    void pinReplacementOccursBeforeNewNoticeInsert() {
        var request = new CreateNoticeRequest("  정기 세션 안내  ", " 본문 ", true, List.of());
        when(images.attach(any(UUID.class), eq(List.of()), eq(7L))).thenReturn(List.of());
        when(repository.create(any(UUID.class), eq("정기 세션 안내"), eq("본문"), eq(true), eq(7L)))
                .thenAnswer(invocation -> new NoticeResponse(invocation.getArgument(0),
                        "정기 세션 안내", "본문", true, new NoticeAuthor("7", "관리자"),
                        List.of(), now, now));

        var created = service.create(request, 7L);

        InOrder order = inOrder(repository, images);
        order.verify(repository).lockPinSlot();
        order.verify(repository).unpinCurrent();
        order.verify(repository).create(any(UUID.class), eq("정기 세션 안내"), eq("본문"),
                eq(true), eq(7L));
        order.verify(images).attach(any(UUID.class), eq(List.of()), eq(7L));
        assertEquals("7", created.author().id());
        assertEquals(now, created.createdAt());
    }

    @Test
    void missingPinDefaultsToFalseAndDoesNotUnpin() {
        when(repository.create(any(UUID.class), eq("공지"), eq("본문"), eq(false), eq(7L)))
                .thenAnswer(invocation -> new NoticeResponse(invocation.getArgument(0),
                        "공지", "본문", false, new NoticeAuthor("7", "관리자"),
                        List.of(), now, now));
        service.create(new CreateNoticeRequest("공지", "본문", null, null), 7L);
        var order = inOrder(repository, images);
        order.verify(repository).create(any(UUID.class), eq("공지"), eq("본문"),
                eq(false), eq(7L));
        order.verify(images).attach(any(UUID.class), eq(List.of()), eq(7L));
    }

    @Test
    void invalidWhitespaceAndDuplicateImagesDoNotTouchStorage() {
        assertThrows(BusinessException.class,
                () -> service.create(new CreateNoticeRequest("  ", "본문", false, null), 7L));
        UUID id = UUID.randomUUID();
        assertThrows(BusinessException.class,
                () -> service.create(new CreateNoticeRequest("공지", "본문", false,
                        List.of(id, id)), 7L));
        verifyNoInteractions(images, repository);
    }

    @Test
    void listingSeparatesPinnedNoticeWithoutDuplication() {
        var author = new NoticeAuthor("7", "관리자");
        var pinned = new NoticeSummary(UUID.randomUUID(), "고정", true, author, now, now);
        var regular = new NoticeSummary(UUID.randomUUID(), "일반", false, author, now, now);
        when(repository.list()).thenReturn(List.of(pinned, regular));

        var result = service.list();

        assertEquals(pinned, result.pinnedNotice());
        assertEquals(List.of(regular), result.items());
        when(repository.list()).thenReturn(List.of());
        assertNull(service.list().pinnedNotice());
        assertEquals(List.of(), service.list().items());
    }
}
