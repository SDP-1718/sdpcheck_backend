package com.sdpcheck.sdpcheck.domain.schedule.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.schedule.dto.request.UpdateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import com.sdpcheck.sdpcheck.domain.schedule.exception.ScheduleErrorCode;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
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

@WebMvcTest(controllers = AdminScheduleController.class, properties = {
        "spring.config.import=", "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000", "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class ScheduleMutationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean ScheduleService service;
    private static final UUID ID = UUID.fromString("28e6b6bb-4f30-47f8-b9c4-54e3b06d18c0");
    private static final String URL = "/api/v1/admin/schedules/" + ID;

    @Test
    void missingOrInvalidTokensReturnContractCode() throws Exception {
        for (String token : List.of("", "Bearer invalid")) {
            mvc.perform(patch(URL).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"location\":null,\"version\":1}"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
            mvc.perform(delete(URL).header("Authorization", token).param("version", "1"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void ordinaryMemberCannotMutate() throws Exception {
        String token = authenticate(Role.MEMBER);
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":null,\"version\":1}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(delete(URL).header("Authorization", token).param("version", "1"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        verifyNoInteractions(service);
    }

    @Test
    void locationNullIsAnExplicitChangeAndResponseMatchesContract() throws Exception {
        String token = authenticate(Role.ADMIN);
        var time = OffsetDateTime.parse("2026-10-02T19:00:00+09:00");
        when(service.update(eq(ID), any())).thenAnswer(invocation -> {
            UpdateScheduleRequest request = invocation.getArgument(1);
            assertTrue(request.locationPresent());
            assertNull(request.location());
            assertFalse(request.titlePresent());
            assertFalse(request.startsAtPresent());
            assertEquals(1, request.version());
            return new ScheduleResponse(ID, "EVENT", "회의", time, null, time, time, 2);
        });
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":null,\"version\":1}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SCHEDULE_UPDATED"))
                .andExpect(jsonPath("$.result.location").value((Object) null))
                .andExpect(jsonPath("$.result.version").value(2));
    }

    @Test
    void offsetDateAndTrimmedTitleArePassedToService() throws Exception {
        String token = authenticate(Role.ADMIN);
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  회의  \",\"startsAt\":\"2020-01-01T10:00:00Z\",\"version\":1}"))
                .andExpect(status().isOk());
        verify(service).update(eq(ID), argThat(request -> "회의".equals(request.title())
                && request.startsAt().equals(OffsetDateTime.parse("2020-01-01T10:00:00Z"))
                && !request.locationPresent()));
    }

    @Test
    void invalidPatchBodiesNeverReachService() throws Exception {
        String token = authenticate(Role.ADMIN);
        for (String body : List.of(
                "{}", "null", "[]", "{\"version\":1}",
                "{\"title\":\"회의\"}", "{\"title\":null,\"version\":1}",
                "{\"title\":\"  \",\"version\":1}", "{\"startsAt\":null,\"version\":1}",
                "{\"location\":null,\"version\":null}", "{\"location\":null,\"version\":0}",
                "{\"location\":null,\"version\":1.5}", "{\"location\":null,\"version\":\"1\"}",
                "{\"title\":123,\"version\":1}", "{\"location\":{},\"version\":1}",
                "{\"title\":\"회의\",\"type\":\"SESSION\",\"version\":1}",
                "{\"startsAt\":\"2026-10-02T19:00:00\",\"version\":1}",
                "{\"startsAt\":\"2026-02-30T19:00:00+09:00\",\"version\":1}",
                "{\"title\":\"" + "가".repeat(101) + "\",\"version\":1}",
                "{\"location\":\"" + "가".repeat(201) + "\",\"version\":1}")) {
            mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void deleteRequiresPositiveVersionAndReturnsNoBody() throws Exception {
        String token = authenticate(Role.ADMIN);
        mvc.perform(delete(URL).header("Authorization", token).param("version", "1"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).delete(ID, 1);
        clearInvocations(service);
        for (String version : List.of("", "0", "-1", "1.5", "bad")) {
            mvc.perform(delete(URL).header("Authorization", token).param("version", version))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(delete(URL).header("Authorization", token)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void malformedIdsAreValidationErrors() throws Exception {
        String token = authenticate(Role.ADMIN);
        mvc.perform(patch("/api/v1/admin/schedules/not-a-uuid").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"location\":null,\"version\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(delete("/api/v1/admin/schedules/not-a-uuid").header("Authorization", token).param("version", "1"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(service);
    }

    @Test
    void notFoundAndConflictUseErrorEnvelope() throws Exception {
        String token = authenticate(Role.ADMIN);
        when(service.update(eq(ID), any())).thenThrow(new BusinessException(CommonErrorCode.RESOURCE_NOT_FOUND));
        doThrow(new BusinessException(ScheduleErrorCode.VERSION_CONFLICT)).when(service).delete(ID, 1);
        mvc.perform(patch(URL).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"location\":null,\"version\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(delete(URL).header("Authorization", token).param("version", "1"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"))
                .andExpect(jsonPath("$.result").value((Object) null));
    }

    private String authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(new Member("tester", "hash", "테스터", 17, role)));
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
