package com.sdpcheck.sdpcheck.domain.notice.controller;

import com.sdpcheck.sdpcheck.domain.file.exception.FileErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeAuthor;
import com.sdpcheck.sdpcheck.domain.notice.dto.response.NoticeResponse;
import com.sdpcheck.sdpcheck.domain.notice.exception.NoticeErrorCode;
import com.sdpcheck.sdpcheck.domain.notice.service.NoticeService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminNoticeController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class NoticeMutationControllerTest {
    private static final UUID ID = UUID.randomUUID();
    private static final String URL = "/api/v1/admin/notices/" + ID;
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean NoticeService service;

    @Test
    void missingAndInvalidTokensReturnContractCode() throws Exception {
        for (String token : List.of("", "Bearer invalid")) {
            mvc.perform(patch(URL).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"공지\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("WWW-Authenticate", "Bearer"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(delete(URL).header("Authorization", token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryMemberCannotMutate() throws Exception {
        String token = authenticate(Role.MEMBER);
        mvc.perform(patch(URL).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"공지\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete(URL).header("Authorization", token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void partialUpdatePreservesPresenceAndUsesExpectedEnvelope() throws Exception {
        String token = authenticate(Role.ADMIN);
        var now = OffsetDateTime.parse("2026-10-30T19:00:00+09:00");
        when(service.update(eq(ID), any(), eq(1L))).thenAnswer(invocation -> {
            var request = (com.sdpcheck.sdpcheck.domain.notice.dto.request.UpdateNoticeRequest) invocation.getArgument(1);
            assertEquals("변경", request.title());
            assertNull(request.content());
            assertNull(request.pinned());
            assertNull(request.imageIds());
            return new NoticeResponse(ID, "변경", "본문", false,
                    new NoticeAuthor("7", "최초 작성자"), List.of(), now, now);
        });
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  변경  \"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("NOTICE_UPDATED"))
                .andExpect(jsonPath("$.result.author.id").value("7"))
                .andExpect(jsonPath("$.result.images").isEmpty());
    }

    @Test
    void falsePinAndEmptyImageArrayAreExplicitChanges() throws Exception {
        String token = authenticate(Role.ADMIN);
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isPinned\":false,\"imageIds\":[]}"))
                .andExpect(status().isOk());
        verify(service).update(eq(ID), argThat(request -> Boolean.FALSE.equals(request.pinned())
                && request.imageIds().isEmpty() && request.title() == null && request.content() == null), eq(1L));
    }

    @Test
    void invalidBodiesNeverReachService() throws Exception {
        String token = authenticate(Role.ADMIN);
        String fileId = UUID.randomUUID().toString();
        for (String body : List.of(
                "{}", "null", "[]", "{\"title\":null}", "{\"content\":null}",
                "{\"isPinned\":null}", "{\"imageIds\":null}",
                "{\"title\":\"\"}", "{\"title\":\" \\t\\n\"}", "{\"content\":\" \"}",
                "{\"title\":123}", "{\"content\":true}", "{\"isPinned\":\"true\"}",
                "{\"isPinned\":1}", "{\"pinned\":true}", "{\"imageIds\":{}}", "{\"imageIds\":[null]}",
                "{\"imageIds\":[123]}", "{\"imageIds\":[\"bad\"]}",
                "{\"imageIds\":[\"1-1-1-1-1\"]}",
                "{\"imageIds\":[\"" + fileId + "\",\"" + fileId + "\"]}",
                "{\"title\":\"공지\",\"authorId\":7}")) {
            mvc.perform(patch(URL).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void deleteReturns204WithNoBody() throws Exception {
        String token = authenticate(Role.ADMIN);
        mvc.perform(delete(URL).header("Authorization", token))
                .andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(service).delete(ID);
    }

    @Test
    void malformedPathIdsReturn400() throws Exception {
        String token = authenticate(Role.ADMIN);
        mvc.perform(patch("/api/v1/admin/notices/bad").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPinned\":false}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(delete("/api/v1/admin/notices/bad").header("Authorization", token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void missingNoticeAndInvalidAttachmentsUseErrorEnvelope() throws Exception {
        String token = authenticate(Role.ADMIN);
        when(service.update(eq(ID), any(), eq(1L))).thenThrow(new BusinessException(FileErrorCode.FILE_NOT_READY));
        doThrow(new BusinessException(NoticeErrorCode.RESOURCE_NOT_FOUND)).when(service).delete(ID);
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"imageIds\":[]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("FILE_NOT_READY"))
                .andExpect(jsonPath("$.result").value((Object) null));
        mvc.perform(delete(URL).header("Authorization", token))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private String authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(new Member("tester", "hash", "테스터", 17, role)));
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
