package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeImage;
import com.sdpcheck.sdpcheck.domain.file.dto.response.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface NoticeImageService {
    ImageUploadResponse upload(MultipartFile file, long ownerId);

    List<NoticeImage> attach(UUID noticeId, List<UUID> imageIds, long ownerId);

    ImageContent get(UUID fileId);

    record ImageContent(byte[] bytes, String contentType) {
    }
}
