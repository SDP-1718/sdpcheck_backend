package com.sdpcheck.sdpcheck.domain.schedule.controller;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.SchedulePageResponse;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
import com.sdpcheck.sdpcheck.domain.schedule.service.ScheduleQueryService;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ScheduleController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class ScheduleControllerTest {
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID GENERAL_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-09T19:00:00+09:00");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean ScheduleQueryService queryService;

    @ParameterizedTest
    @EnumSource(Role.class)
    void memberAndAdminCanReadDefaultPage(Role role) throws Exception {
        authenticate(role);
        when(queryService.getSchedules(0, 10)).thenReturn(defaultPage());

        mvc.perform(get("/api/v1/schedules").header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalElements").value(11))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.items[0].id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.items[0].type").value("SESSION"))
                .andExpect(jsonPath("$.result.items[0].title").value("정기 세션"))
                .andExpect(jsonPath("$.result.items[0].startsAt")
                        .value("2026-10-09T19:00:00+09:00"))
                .andExpect(jsonPath("$.result.items[0].location").hasJsonPath())
                .andExpect(jsonPath("$.result.items[0].location").value((Object) null))
                .andExpect(jsonPath("$.result.items[0].canManage").doesNotExist())
                .andExpect(jsonPath("$.result.items[0].version").doesNotExist());

        verify(queryService).getSchedules(0, 10);
    }

    @Test
    void customPageAndSizeAreForwardedAndReturned() throws Exception {
        authenticate(Role.MEMBER);
        var response = new SchedulePageResponse(List.of(generalSummary()), 2, 3, 7, 3, false);
        when(queryService.getSchedules(2, 3)).thenReturn(response);

        mvc.perform(get("/api/v1/schedules?page=2&size=3")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(3))
                .andExpect(jsonPath("$.result.totalElements").value(7))
                .andExpect(jsonPath("$.result.totalPages").value(3))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.items[0].id").value(GENERAL_ID.toString()))
                .andExpect(jsonPath("$.result.items[0].type").value("GENERAL"))
                .andExpect(jsonPath("$.result.items[0].location").value("학생회관"));

        verify(queryService).getSchedules(2, 3);
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/schedules"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(queryService);
    }

    @Test
    void invalidBearerIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/schedules").header(HttpHeaders.AUTHORIZATION, "Bearer broken.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(queryService);
    }

    @Test
    void invalidPageOrSizeIsRejectedBeforeService() throws Exception {
        authenticate(Role.MEMBER);

        for (String query : List.of(
                "?page=not-a-number",
                "?size=not-a-number",
                "?page=-1",
                "?size=0",
                "?size=101")) {
            mvc.perform(get("/api/v1/schedules" + query).header(HttpHeaders.AUTHORIZATION, token()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(queryService);
    }

    private SchedulePageResponse defaultPage() {
        return new SchedulePageResponse(List.of(sessionSummary()), 0, 10, 11, 2, true);
    }

    private ScheduleSummary sessionSummary() {
        return new ScheduleSummary(SESSION_ID, ScheduleType.SESSION, "정기 세션", STARTS_AT, null);
    }

    private ScheduleSummary generalSummary() {
        return new ScheduleSummary(GENERAL_ID, ScheduleType.GENERAL, "운영진 회의", STARTS_AT,
                "학생회관");
    }

    private void authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                new Member("schedule-reader", "hash", "회원", 17, role)));
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
