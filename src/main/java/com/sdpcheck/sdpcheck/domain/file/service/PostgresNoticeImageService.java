package com.sdpcheck.sdpcheck.domain.file.service;

import com.sdpcheck.sdpcheck.domain.file.dto.response.ImageUploadResponse;
import com.sdpcheck.sdpcheck.domain.file.exception.FileErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeImage;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class PostgresNoticeImageService implements NoticeImageService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresNoticeImageService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ImageUploadResponse upload(MultipartFile file, long ownerId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
        String fileName = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (fileName == null || fileName.isBlank() || fileName.length() > 255) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        if (contentType == null
                || !List.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
            throw new BusinessException(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, exception);
        }
        if (bytes.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(CommonErrorCode.PAYLOAD_TOO_LARGE);
        }
        if (bytes.length == 0 || !imageSignatureMatches(bytes, contentType)) {
            throw new BusinessException(FileErrorCode.INVALID_IMAGE);
        }
        jdbc.getJdbcTemplate().update("""
                DELETE FROM uploaded_files
                WHERE status = 'PENDING' AND attach_before <= now()
                """);
        UUID fileId = UUID.randomUUID();
        OffsetDateTime attachBefore = OffsetDateTime.now(SEOUL).plusHours(24);
        var params = new MapSqlParameterSource()
                .addValue("id", fileId).addValue("ownerId", ownerId)
                .addValue("name", fileName).addValue("contentType", contentType)
                .addValue("sizeBytes", bytes.length).addValue("data", bytes, Types.BINARY)
                .addValue("attachBefore", attachBefore);
        jdbc.update("""
                INSERT INTO uploaded_files
                    (id, owner_id, original_name, content_type, size_bytes, data, attach_before)
                VALUES (:id, :ownerId, :name, :contentType, :sizeBytes, :data, :attachBefore)
                """, params);
        return new ImageUploadResponse(fileId, attachBefore);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<NoticeImage> attach(UUID noticeId, List<UUID> imageIds, long ownerId) {
        lockAndValidate(imageIds, Set.of(), ownerId);
        return insertAttachments(noticeId, imageIds);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoticeImage> findAttached(UUID noticeId) {
        return jdbc.query("""
                SELECT ni.file_id FROM notice_images ni
                JOIN uploaded_files f ON f.id = ni.file_id
                WHERE ni.notice_id = :id AND f.status = 'ATTACHED'
                ORDER BY ni.position
                """, new MapSqlParameterSource("id", noticeId), (rs, rowNum) -> {
            UUID id = rs.getObject("file_id", UUID.class);
            return new NoticeImage(id, "/api/v1/files/" + id, null);
        });
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<NoticeImage> replace(UUID noticeId, List<UUID> imageIds, long ownerId) {
        Set<UUID> existingIds = new HashSet<>();
        findAttached(noticeId).forEach(image -> existingIds.add(image.fileId()));
        lockAndValidate(imageIds, existingIds, ownerId);
        // Validate every file first. Removal, reordering, and new attachments share the notice transaction.
        jdbc.update("DELETE FROM notice_images WHERE notice_id = :id",
                new MapSqlParameterSource("id", noticeId));
        return insertAttachments(noticeId, imageIds);
    }

    private void lockAndValidate(List<UUID> imageIds, Set<UUID> existingIds, long ownerId) {
        if (imageIds.stream().anyMatch(id -> id == null)
                || imageIds.stream().distinct().count() != imageIds.size()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }
        // Use one lock order for both creation and replacement, regardless of display order.
        for (UUID fileId : imageIds.stream().sorted().toList()) {
            FileState state;
            try {
                state = jdbc.queryForObject("""
                        SELECT owner_id, status, attach_before, content_type, size_bytes,
                               octet_length(data) AS actual_size, substring(data from 1 for 12) AS signature
                        FROM uploaded_files WHERE id = :id FOR UPDATE
                        """, new MapSqlParameterSource("id", fileId), (rs, rowNum) ->
                        new FileState(rs.getLong("owner_id"), rs.getString("status"),
                                rs.getObject("attach_before", OffsetDateTime.class).toInstant(),
                                rs.getString("content_type"), rs.getLong("size_bytes"),
                                rs.getLong("actual_size"), rs.getBytes("signature")));
            } catch (EmptyResultDataAccessException exception) {
                throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
            }
            if (existingIds.contains(fileId)) {
                // Another admin may edit the notice without owning its original uploads.
                if (!"ATTACHED".equals(state.status())) {
                    throw new BusinessException(FileErrorCode.FILE_NOT_READY);
                }
                continue;
            }
            if (state.ownerId() != ownerId) {
                throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
            }
            if (!"PENDING".equals(state.status()) || !state.attachBefore().isAfter(Instant.now())) {
                throw new BusinessException(FileErrorCode.FILE_NOT_READY);
            }
            if (state.sizeBytes() < 1 || state.sizeBytes() > MAX_IMAGE_BYTES
                    || state.sizeBytes() != state.actualSize()
                    || !imageSignatureMatches(state.signature(), state.contentType())) {
                throw new BusinessException(FileErrorCode.INVALID_IMAGE);
            }
        }
    }

    private List<NoticeImage> insertAttachments(UUID noticeId, List<UUID> imageIds) {
        List<NoticeImage> images = new ArrayList<>();
        for (int position = 0; position < imageIds.size(); position++) {
            UUID fileId = imageIds.get(position);
            var params = new MapSqlParameterSource()
                    .addValue("fileId", fileId).addValue("noticeId", noticeId)
                    .addValue("position", position);
            jdbc.update("UPDATE uploaded_files SET status = 'ATTACHED' WHERE id = :fileId", params);
            jdbc.update("""
                    INSERT INTO notice_images (notice_id, file_id, position)
                    VALUES (:noticeId, :fileId, :position)
                    """, params);
            images.add(new NoticeImage(fileId, "/api/v1/files/" + fileId, null));
        }
        return images;
    }

    @Override
    @Transactional(readOnly = true)
    public ImageContent get(UUID fileId) {
        try {
            return jdbc.queryForObject("""
                    SELECT f.data, f.content_type
                    FROM uploaded_files f
                    JOIN notice_images ni ON ni.file_id = f.id
                    JOIN notices n ON n.id = ni.notice_id
                    WHERE f.id = :id AND f.status = 'ATTACHED'
                          AND n.deleted_at IS NULL AND n.is_published = true
                    """, new MapSqlParameterSource("id", fileId), (rs, rowNum) ->
                    new ImageContent(rs.getBytes("data"), rs.getString("content_type")));
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
        }
    }

    private static boolean imageSignatureMatches(byte[] bytes, String mime) {
        return switch (mime) {
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 255) == 255
                    && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 255) == 137
                    && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71
                    && bytes[4] == 13 && bytes[5] == 10 && bytes[6] == 26 && bytes[7] == 10;
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 82 && bytes[1] == 73
                    && bytes[2] == 70 && bytes[3] == 70 && bytes[8] == 87
                    && bytes[9] == 69 && bytes[10] == 66 && bytes[11] == 80;
            default -> false;
        };
    }

    private record FileState(long ownerId, String status, Instant attachBefore, String contentType,
                             long sizeBytes, long actualSize, byte[] signature) {
    }
}
