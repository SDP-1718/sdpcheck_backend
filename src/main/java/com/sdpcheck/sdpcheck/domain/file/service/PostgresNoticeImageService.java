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
import java.util.List;
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
        List<NoticeImage> images = new ArrayList<>();
        for (int position = 0; position < imageIds.size(); position++) {
            UUID fileId = imageIds.get(position);
            FileState state;
            try {
                state = jdbc.queryForObject("""
                        SELECT owner_id, status, attach_before
                        FROM uploaded_files WHERE id = :id FOR UPDATE
                        """, new MapSqlParameterSource("id", fileId), (rs, rowNum) ->
                        new FileState(rs.getLong("owner_id"), rs.getString("status"),
                                rs.getObject("attach_before", OffsetDateTime.class).toInstant()));
            } catch (EmptyResultDataAccessException exception) {
                throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
            }
            if (state.ownerId() != ownerId) {
                throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
            }
            if (!"PENDING".equals(state.status()) || !state.attachBefore().isAfter(Instant.now())) {
                throw new BusinessException(FileErrorCode.FILE_NOT_READY);
            }
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
                    WHERE f.id = :id AND f.status = 'ATTACHED' AND n.deleted_at IS NULL
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

    private record FileState(long ownerId, String status, Instant attachBefore) {
    }
}
