package com.sdpcheck.sdpcheck.domain.file.controller;

import io.swagger.v3.oas.annotations.Operation;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.file.dto.response.ImageUploadResponse;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.response.ApiResponse;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
@SecurityRequirement(name = "bearerAuth")
public class ImageUploadController {
    private final NoticeImageService service;

    public ImageUploadController(NoticeImageService service) {
        this.service = service;
    }

    @Operation(
            operationId = "upload", summary = "공지 이미지 업로드",
            description = "관리자가 최대 5 MiB의 JPEG·PNG·WebP 파일을 업로드합니다. PostgreSQL에 저장한 뒤 공지 연결용 fileId를 반환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201", description = "이미지 업로드 완료", useReturnTypeSchema = true)
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ImageUploadResponse>> upload(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
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

    @Operation(summary = "공지 첨부 이미지 조회", description = "로그인한 회원이 공개 공지에 연결된 이미지를 조회합니다. 응답은 이미지 파일 바이트입니다.")
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
