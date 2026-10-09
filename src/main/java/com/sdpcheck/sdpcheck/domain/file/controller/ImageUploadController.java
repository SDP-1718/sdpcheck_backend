package com.sdpcheck.sdpcheck.domain.file.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.file.dto.response.ImageUploadResponse;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class ImageUploadController {
    private final NoticeImageService service;

    public ImageUploadController(NoticeImageService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestPart("file") MultipartFile file) {
        if (member == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (member.role() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        ImageUploadResponse result = service.upload(file, member.memberId());
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(new ApiResponse<>(true, "IMAGE_UPLOADED",
                        "이미지가 업로드되었습니다.", result));
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> get(@PathVariable UUID fileId) {
        var image = service.get(fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
    }
}
