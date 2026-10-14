package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDetailResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticePageResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import com.sdpcheck.sdpcheck.domain.notice.exception.NoticeErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.repository.NoticeQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NoticeQueryServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-08T12:00:00+09:00");
    private static final NoticeAuthor AUTHOR = new NoticeAuthor("7", "관리자");

    private NoticeQueryRepository repository;
    private NoticeQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(NoticeQueryRepository.class);
        service = new NoticeQueryService(repository);
    }

    @Test
    void returnsEmptyPageMetadata() {
        when(repository.countVisible()).thenReturn(0L);

        NoticePageResponse result = service.getNotices(0, 10);

        assertAll(
                () -> assertEquals(List.of(), result.items()),
                () -> assertEquals(0, result.page()),
                () -> assertEquals(10, result.size()),
                () -> assertEquals(0L, result.totalElements()),
                () -> assertEquals(0, result.totalPages()),
                () -> assertEquals(false, result.hasNext())
        );
    }

    @Test
    void returnsRequestedPageWithMetadata() {
        NoticeSummary pinned = summary("고정 공지", true);
        NoticeSummary regular = summary("일반 공지", false);
        when(repository.countVisible()).thenReturn(25L);
        when(repository.findVisiblePage(0, 10)).thenReturn(List.of(pinned, regular));

        NoticePageResponse result = service.getNotices(0, 10);

        assertAll(
                () -> assertEquals(List.of(pinned, regular), result.items()),
                () -> assertEquals(0, result.page()),
                () -> assertEquals(10, result.size()),
                () -> assertEquals(25L, result.totalElements()),
                () -> assertEquals(3, result.totalPages()),
                () -> assertEquals(true, result.hasNext())
        );
        verify(repository).findVisiblePage(0, 10);
    }

    @Test
    void lastPartialPageHasNoNextPage() {
        when(repository.countVisible()).thenReturn(25L);
        when(repository.findVisiblePage(2, 10)).thenReturn(List.of(
                summary("공지 21", false), summary("공지 22", false), summary("공지 23", false),
                summary("공지 24", false), summary("공지 25", false)
        ));

        NoticePageResponse result = service.getNotices(2, 10);

        assertAll(
                () -> assertEquals(5, result.items().size()),
                () -> assertEquals(2, result.page()),
                () -> assertEquals(3, result.totalPages()),
                () -> assertEquals(false, result.hasNext())
        );
    }

    @Test
    void outOfRangePageHasNoNextPage() {
        when(repository.countVisible()).thenReturn(25L);
        when(repository.findVisiblePage(3, 10)).thenReturn(List.of());

        NoticePageResponse result = service.getNotices(3, 10);

        assertAll(
                () -> assertEquals(List.of(), result.items()),
                () -> assertEquals(3, result.page()),
                () -> assertEquals(25L, result.totalElements()),
                () -> assertEquals(3, result.totalPages()),
                () -> assertEquals(false, result.hasNext())
        );
    }

    @Test
    void invalidPageOrSizeDoesNotQueryRepository() {
        assertValidationError(() -> service.getNotices(-1, 10));
        assertValidationError(() -> service.getNotices(0, 0));
        assertValidationError(() -> service.getNotices(0, 101));

        verifyNoInteractions(repository);
    }

    @Test
    void detailPreservesRepositoryImageOrder() {
        UUID noticeId = UUID.randomUUID();
        NoticeDetailResponse header = detail(noticeId, List.of());
        List<String> imageUrls = List.of(
                "https://cdn.example.com/notices/second.png",
                "https://cdn.example.com/notices/first.png"
        );
        when(repository.findVisibleById(noticeId)).thenReturn(Optional.of(header));
        when(repository.findImageUrls(noticeId)).thenReturn(imageUrls);

        NoticeDetailResponse result = service.getNotice(noticeId);

        assertEquals(detail(noticeId, imageUrls), result);
        verify(repository).findVisibleById(noticeId);
        verify(repository).findImageUrls(noticeId);
    }

    @Test
    void detailReturnsEmptyImageListWhenNoImagesExist() {
        UUID noticeId = UUID.randomUUID();
        when(repository.findVisibleById(noticeId)).thenReturn(Optional.of(detail(noticeId, List.of())));
        when(repository.findImageUrls(noticeId)).thenReturn(List.of());

        NoticeDetailResponse result = service.getNotice(noticeId);

        assertEquals(List.of(), result.imageUrls());
    }

    @Test
    void missingNoticeDoesNotQueryImages() {
        UUID noticeId = UUID.randomUUID();
        when(repository.findVisibleById(noticeId)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.getNotice(noticeId));

        assertSame(NoticeErrorCode.NOTICE_NOT_FOUND, exception.getErrorCode());
        verify(repository).findVisibleById(noticeId);
        verify(repository, never()).findImageUrls(any(UUID.class));
    }

    private NoticeSummary summary(String title, boolean isPinned) {
        return new NoticeSummary(UUID.randomUUID(), title, isPinned, AUTHOR, NOW, NOW);
    }

    private NoticeDetailResponse detail(UUID id, List<String> imageUrls) {
        return new NoticeDetailResponse(id, "공지 제목", "공지 본문", true, AUTHOR, imageUrls, NOW, NOW);
    }

    private void assertValidationError(Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertSame(CommonErrorCode.VALIDATION_ERROR, exception.getErrorCode());
    }
}
