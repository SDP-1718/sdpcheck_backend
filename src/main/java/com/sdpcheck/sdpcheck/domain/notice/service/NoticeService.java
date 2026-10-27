package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.dto.request.CreateNoticeRequest;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeListResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeSummary;
import com.sdpcheck.sdpcheck.domain.notice.repository.NoticeRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NoticeService {
    private final NoticeRepository repository;
    private final NoticeImageService imageService;

    public NoticeService(NoticeRepository repository, NoticeImageService imageService) {
        this.repository = repository;
        this.imageService = imageService;
    }

    @Transactional
    public NoticeResponse create(CreateNoticeRequest request, long authorId) {
        String title = request.title().strip();
        String content = request.content().strip();
        if (title.isEmpty() || content.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        List<UUID> imageIds = request.imageIds() == null ? List.of() : request.imageIds();
        if (imageIds.stream().anyMatch(id -> id == null)
                || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        boolean pinned = Boolean.TRUE.equals(request.isPinned());
        UUID noticeId = UUID.randomUUID();
        if (pinned) {
            repository.lockPinSlot();
            repository.unpinCurrent();
        }
        NoticeResponse created = repository.create(noticeId, title, content, pinned, authorId);
        var images = imageService.attach(noticeId, imageIds, authorId);
        return new NoticeResponse(created.id(), created.title(), created.content(),
                created.isPinned(), created.author(), images, created.createdAt(), created.updatedAt());
    }

    @Transactional(readOnly = true)
    public NoticeListResponse list() {
        List<NoticeSummary> notices = repository.list();
        NoticeSummary pinned = null;
        List<NoticeSummary> items = new ArrayList<>();
        for (NoticeSummary notice : notices) {
            if (notice.isPinned()) {
                pinned = notice;
            } else {
                items.add(notice);
            }
        }
        return new NoticeListResponse(pinned, items);
    }
}
