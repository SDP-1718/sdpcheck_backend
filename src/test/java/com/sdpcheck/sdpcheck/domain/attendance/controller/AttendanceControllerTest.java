package com.sdpcheck.sdpcheck.domain.attendance.controller;

import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryItem;
import com.sdpcheck.sdpcheck.domain.attendance.dto.response.AttendanceHistoryPageResponse;
import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.attendance.service.AttendanceQueryService;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AttendanceController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class,
        GlobalExceptionHandler.class})
class AttendanceControllerTest {

    private static final UUID PRESENT_SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID LATE_SESSION_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ABSENT_SESSION_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final OffsetDateTime STARTS_AT = OffsetDateTime.parse("2026-10-09T19:00:00+09:00");
    private static final OffsetDateTime PRESENT_CHECKED_IN_AT = OffsetDateTime.parse("2026-10-09T18:59:00+09:00");
    private static final OffsetDateTime LATE_CHECKED_IN_AT = OffsetDateTime.parse("2026-10-09T19:08:00+09:00");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository memberRepository;
    @MockitoBean AttendanceQueryService queryService;

    @ParameterizedTest
    @EnumSource(Role.class)
    void memberAndAdminCanReadTheirOwnDefaultPage(Role role) throws Exception {
        authenticate(role);
        when(queryService.getMyAttendances(1L, 0, 10)).thenReturn(defaultPage());

        mvc.perform(get("/api/v1/members/me/attendances")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(10))
                .andExpect(jsonPath("$.result.totalElements").value(11))
                .andExpect(jsonPath("$.result.totalPages").value(2))
                .andExpect(jsonPath("$.result.hasNext").value(true))
                .andExpect(jsonPath("$.result.items[0].sessionId").value(PRESENT_SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.items[0].sessionTitle").value("정기 세션"))
                .andExpect(jsonPath("$.result.items[0].startsAt").value("2026-10-09T19:00:00+09:00"))
                .andExpect(jsonPath("$.result.items[0].attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$.result.items[0].checkedInAt")
                        .value("2026-10-09T18:59:00+09:00"))
                .andExpect(jsonPath("$.result.items[1].attendanceStatus").value("LATE"))
                .andExpect(jsonPath("$.result.items[1].checkedInAt")
                        .value("2026-10-09T19:08:00+09:00"))
                .andExpect(jsonPath("$.result.items[2].sessionId").value(ABSENT_SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.items[2].attendanceStatus").value("ABSENT"))
                .andExpect(jsonPath("$.result.items[2].checkedInAt").hasJsonPath())
                .andExpect(jsonPath("$.result.items[2].checkedInAt").value((Object) null));

        verify(queryService).getMyAttendances(1L, 0, 10);
    }

    @Test
    void customPaginationIsForwardedAndReturned() throws Exception {
        authenticate(Role.MEMBER);
        var response = new AttendanceHistoryPageResponse(List.of(presentItem()), 2, 3, 7, 3, false);
        when(queryService.getMyAttendances(1L, 2, 3)).thenReturn(response);

        mvc.perform(get("/api/v1/members/me/attendances?page=2&size=3")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.page").value(2))
                .andExpect(jsonPath("$.result.size").value(3))
                .andExpect(jsonPath("$.result.totalElements").value(7))
                .andExpect(jsonPath("$.result.totalPages").value(3))
                .andExpect(jsonPath("$.result.hasNext").value(false))
                .andExpect(jsonPath("$.result.items[0].sessionId").value(PRESENT_SESSION_ID.toString()));

        verify(queryService).getMyAttendances(1L, 2, 3);
    }

    @Test
    void queryParameterCannotOverrideAuthenticatedMemberId() throws Exception {
        authenticate(Role.MEMBER);
        when(queryService.getMyAttendances(1L, 0, 10)).thenReturn(emptyPage());

        mvc.perform(get("/api/v1/members/me/attendances?memberId=2")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk());

        verify(queryService).getMyAttendances(1L, 0, 10);
        verify(queryService, never()).getMyAttendances(2L, 0, 10);
    }

    @Test
    void emptyHistoryIsSuccessful() throws Exception {
        authenticate(Role.MEMBER);
        when(queryService.getMyAttendances(1L, 0, 10)).thenReturn(emptyPage());

        mvc.perform(get("/api/v1/members/me/attendances")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.totalElements").value(0))
                .andExpect(jsonPath("$.result.totalPages").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void anonymousRequestIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/members/me/attendances"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(queryService);
    }

    @Test
    void invalidBearerIsUnauthorized() throws Exception {
        mvc.perform(get("/api/v1/members/me/attendances")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer broken.token"))
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
            mvc.perform(get("/api/v1/members/me/attendances" + query)
                            .header(HttpHeaders.AUTHORIZATION, token()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(queryService);
    }

    @Test
    void operatingSemesterMustBeConfigured() throws Exception {
        authenticate(Role.MEMBER);
        when(queryService.getMyAttendances(1L, 0, 10))
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get("/api/v1/members/me/attendances")
                        .header(HttpHeaders.AUTHORIZATION, token()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"));

        verify(queryService).getMyAttendances(1L, 0, 10);
    }

    private AttendanceHistoryPageResponse defaultPage() {
        return new AttendanceHistoryPageResponse(List.of(presentItem(), lateItem(), absentItem()),
                0, 10, 11, 2, true);
    }

    private AttendanceHistoryPageResponse emptyPage() {
        return new AttendanceHistoryPageResponse(List.of(), 0, 10, 0, 0, false);
    }

    private AttendanceHistoryItem presentItem() {
        return new AttendanceHistoryItem(PRESENT_SESSION_ID, "정기 세션", STARTS_AT,
                AttendanceStatus.PRESENT, PRESENT_CHECKED_IN_AT);
    }

    private AttendanceHistoryItem lateItem() {
        return new AttendanceHistoryItem(LATE_SESSION_ID, "지각 세션", STARTS_AT,
                AttendanceStatus.LATE, LATE_CHECKED_IN_AT);
    }

    private AttendanceHistoryItem absentItem() {
        return new AttendanceHistoryItem(ABSENT_SESSION_ID, "결석 세션", STARTS_AT,
                AttendanceStatus.ABSENT, null);
    }

    private void authenticate(Role role) {
        when(memberRepository.findById(1L)).thenReturn(Optional.of(
                new Member("attendance-reader", "hash", "회원", 17, role)));
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
