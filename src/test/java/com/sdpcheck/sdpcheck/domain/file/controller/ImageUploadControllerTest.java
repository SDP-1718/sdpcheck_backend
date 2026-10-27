package com.sdpcheck.sdpcheck.domain.file.controller;

import com.sdpcheck.sdpcheck.domain.file.dto.response.ImageUploadResponse;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeImageService;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ImageUploadController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class ImageUploadControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean NoticeImageService images;

    @Test
    void onlyAdminCanUpload() throws Exception {
        mvc.perform(multipart("/api/v1/files").file(image())).andExpect(status().isUnauthorized());
        authenticate(Role.MEMBER);
        mvc.perform(multipart("/api/v1/files").file(image())
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(images);
    }

    @Test
    void adminReceivesFileIdForCompletedUpload() throws Exception {
        authenticate(Role.ADMIN);
        UUID fileId = UUID.randomUUID();
        var attachBefore = OffsetDateTime.parse("2026-10-09T12:00:00+09:00");
        when(images.upload(any(MultipartFile.class), eq(1L)))
                .thenReturn(new ImageUploadResponse(fileId, attachBefore));

        mvc.perform(multipart("/api/v1/files").file(image())
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("IMAGE_UPLOADED"))
                .andExpect(jsonPath("$.result.fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.result.attachBefore").exists());
    }

    @Test
    void authenticatedMemberCanReadAttachedImage() throws Exception {
        authenticate(Role.MEMBER);
        UUID fileId = UUID.randomUUID();
        byte[] bytes = { (byte) 137, 80, 78, 71 };
        when(images.get(fileId)).thenReturn(new NoticeImageService.ImageContent(bytes, "image/png"));

        mvc.perform(get("/api/v1/files/" + fileId).header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(bytes));
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("user", "hash", "회원", 17, role)));
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("file", "sample.png", "image/png",
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    }
}
