package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeDetailResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticePageResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import com.sdpcheck.sdpcheck.domain.notice.exception.NoticeErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.repository.NoticeQueryRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class NoticeQueryService {
    public static final int MAX_PAGE_SIZE = 100;
    private final NoticeQueryRepository repository;

    public NoticeQueryService(NoticeQueryRepository repository) {
        this.repository = repository;
    }

    public NoticePageResponse getNotices(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        long totalElements = repository.countVisible();
        int totalPages = Math.toIntExact(Math.ceilDiv(totalElements, (long) size));
        List<NoticeSummary> items = repository.findVisiblePage(page, size);
        return new NoticePageResponse(items, page, size, totalElements, totalPages,
                (long) page + 1 < totalPages);
    }

    public NoticeDetailResponse getNotice(UUID noticeId) {
        NoticeDetailResponse notice = repository.findVisibleById(noticeId)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));
        return new NoticeDetailResponse(notice.id(), notice.title(), notice.content(),
                notice.isPinned(), notice.author(), repository.findImageUrls(noticeId),
                notice.createdAt(), notice.updatedAt());
    }
}
