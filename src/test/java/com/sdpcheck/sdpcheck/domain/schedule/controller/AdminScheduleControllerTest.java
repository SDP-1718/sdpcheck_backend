package com.sdpcheck.sdpcheck.domain.schedule.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.schedule.dto.request.CreateScheduleRequest;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleItem;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleListResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleResponse;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminScheduleController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class AdminScheduleControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean ScheduleService service;

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get("/api/v1/admin/schedules"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/admin/schedules").contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void memberCannotListOrCreate() throws Exception {
        authenticate(Role.MEMBER);
        String token = "Bearer " + jwtProvider.createAccessToken(1L);
        mvc.perform(get("/api/v1/admin/schedules").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/schedules").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminCreatesEventAndGetsLocationHeader() throws Exception {
        authenticate(Role.ADMIN);
        UUID id = UUID.randomUUID();
        var time = OffsetDateTime.parse("2026-10-02T19:00:00+09:00");
        when(service.create(any(CreateScheduleRequest.class), eq(1L)))
                .thenReturn(new ScheduleResponse(id, "EVENT", "운영진 회의", time, null, time, time, 1));

        mvc.perform(post("/api/v1/admin/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L))
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/admin/schedules/" + id))
                .andExpect(jsonPath("$.code").value("SCHEDULE_CREATED"))
                .andExpect(jsonPath("$.result.type").value("EVENT"))
                .andExpect(jsonPath("$.result.version").value(1));
    }

    @Test
    void invalidCreationFieldsAreRejected() throws Exception {
        authenticate(Role.ADMIN);
        String token = "Bearer " + jwtProvider.createAccessToken(1L);
        for (String body : List.of(
                "{\"title\":\" \",\"startsAt\":\"2026-10-02T19:00:00+09:00\"}",
                "{\"title\":\"회의\",\"startsAt\":\"2026-10-02T19:00:00\"}",
                "{\"title\":\"회의\",\"startsAt\":\"2026-10-02T19:00:00+09:00\",\"type\":\"SESSION\"}")) {
            mvc.perform(post("/api/v1/admin/schedules")
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(service);
    }

    @Test
    void adminListsBothEventAndSessionWithPagination() throws Exception {
        authenticate(Role.ADMIN);
        var startsAt = OffsetDateTime.parse("2026-10-02T19:00:00+09:00");
        var event = new ScheduleItem(UUID.randomUUID(), "EVENT", "운영진 회의", startsAt,
                null, true, 1);
        var session = new ScheduleItem(UUID.randomUUID(), "SESSION", "정기 세션", startsAt,
                null, false, null);
        when(service.list(eq(LocalDate.of(2026, 10, 1)), eq(LocalDate.of(2026, 11, 1)),
                eq(null), eq(1), eq(20))).thenReturn(new ScheduleListResponse(
                List.of(event, session), new ScheduleListResponse.Page(1, 20, 2, 1)));

        mvc.perform(get("/api/v1/admin/schedules")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtProvider.createAccessToken(1L))
                        .param("from", "2026-10-01").param("to", "2026-11-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SCHEDULE_LIST_OK"))
                .andExpect(jsonPath("$.result.items[0].type").value("EVENT"))
                .andExpect(jsonPath("$.result.items[0].canManage").value(true))
                .andExpect(jsonPath("$.result.items[1].type").value("SESSION"))
                .andExpect(jsonPath("$.result.items[1].canManage").value(false))
                .andExpect(jsonPath("$.result.items[1].version").value((Object) null))
                .andExpect(jsonPath("$.result.page.totalElements").value(2));
    }

    private void authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                new Member("tester", "hash", "테스터", 17, role)));
    }

    private String validBody() {
        return "{\"title\":\"운영진 회의\",\"startsAt\":\"2026-10-02T19:00:00+09:00\"}";
    }
}
