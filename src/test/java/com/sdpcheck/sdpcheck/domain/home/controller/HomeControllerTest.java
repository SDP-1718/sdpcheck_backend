package com.sdpcheck.sdpcheck.domain.home.controller;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.home.dto.response.HomeResponse;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeCheckInStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeSessionStatus;
import com.sdpcheck.sdpcheck.domain.home.service.HomeQueryService;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;
import com.sdpcheck.sdpcheck.domain.schedule.enums.ScheduleType;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HomeController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class HomeControllerTest {
    private static final String PATH = "/api/v1/home";
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID NOTICE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SCHEDULE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final OffsetDateTime SERVER_TIME = OffsetDateTime.parse("2026-10-09T19:00:00+09:00");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean HomeQueryService service;

    @ParameterizedTest
    @EnumSource(Role.class)
    void memberAndAdminReceiveTheirOwnCompleteHomeResponse(Role role) throws Exception {
        authenticate(role);
        when(service.getHome(1L)).thenReturn(fullHome(role));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result", aMapWithSize(6)))
                .andExpect(jsonPath("$.result.serverTime").value("2026-10-09T19:00:00+09:00"))
                .andExpect(jsonPath("$.result.member", aMapWithSize(3)))
                .andExpect(jsonPath("$.result.member.name").value("상우"))
                .andExpect(jsonPath("$.result.member.generation").value(17))
                .andExpect(jsonPath("$.result.member.role").value(role.name()))
                .andExpect(jsonPath("$.result.session", aMapWithSize(7)))
                .andExpect(jsonPath("$.result.session.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.session.title").value("정규 세션"))
                .andExpect(jsonPath("$.result.session.scheduledStartAt")
                        .value("2026-10-09T18:00:00+09:00"))
                .andExpect(jsonPath("$.result.session.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.result.session.attendanceStatus").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.result.session.checkInStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.result.session.checkedInAt").hasJsonPath())
                .andExpect(jsonPath("$.result.session.checkedInAt").value((Object) null))
                .andExpect(jsonPath("$.result.statistics", aMapWithSize(4)))
                .andExpect(jsonPath("$.result.statistics.periodCode").value("2026-2H"))
                .andExpect(jsonPath("$.result.statistics.attendanceRate").value(67))
                .andExpect(jsonPath("$.result.statistics.attendedSessionCount").value(8))
                .andExpect(jsonPath("$.result.statistics.totalSessionCount").value(12))
                .andExpect(jsonPath("$.result.notices[0].id").value(NOTICE_ID.toString()))
                .andExpect(jsonPath("$.result.notices[0].title").value("최신 공지"))
                .andExpect(jsonPath("$.result.notices[0].createdAt").value("2026-10-08T19:00:00+09:00"))
                .andExpect(jsonPath("$.result.upcomingSchedules[0].id").value(SCHEDULE_ID.toString()))
                .andExpect(jsonPath("$.result.upcomingSchedules[0].type").value("SESSION"))
                .andExpect(jsonPath("$.result.upcomingSchedules[0].startsAt")
                        .value("2026-10-10T19:00:00+09:00"));

        verify(service).getHome(1L);
    }

    @Test
    void nullSessionAndEmptyCollectionsRemainExplicitInTheResponse() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getHome(1L)).thenReturn(new HomeResponse(
                SERVER_TIME,
                new HomeResponse.MemberInfo("상우", 17, Role.MEMBER),
                null,
                new HomeResponse.Statistics("2026-2H", null, 0, 0),
                List.of(),
                List.of()));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.session").hasJsonPath())
                .andExpect(jsonPath("$.result.session").value((Object) null))
                .andExpect(jsonPath("$.result.statistics.attendanceRate").hasJsonPath())
                .andExpect(jsonPath("$.result.statistics.attendanceRate").value((Object) null))
                .andExpect(jsonPath("$.result.notices").isEmpty())
                .andExpect(jsonPath("$.result.upcomingSchedules").isEmpty());

        verify(service).getHome(1L);
    }

    @Test
    void queryParametersCannotOverrideTheAuthenticatedMember() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getHome(1L)).thenReturn(fullHome(Role.MEMBER));

        mvc.perform(get(PATH).queryParam("memberId", "2").queryParam("periodCode", "2025-1H")
                        .header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.member.name").value("상우"));

        verify(service).getHome(1L);
        verify(service, never()).getHome(2L);
    }

    @Test
    void anonymousRequestIsUnauthorizedWithBearerChallengeBeforeServiceAccess() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verifyNoInteractions(service);
    }

    @Test
    void invalidExpiredAndRefreshTokensAreUnauthorizedWithBearerChallenge() throws Exception {
        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : new String[] {
                "Bearer broken.token",
                "Bearer " + expired.createAccessToken(1L),
                "Bearer " + jwtProvider.createRefreshToken(1L)
        }) {
            mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, authorization))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void deletedMemberCannotReadHomeWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void missingMemberFromTheHomeQueryUsesTheExisting401Contract() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getHome(1L)).thenThrow(new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        verify(service).getHome(1L);
    }

    @Test
    void missingOperatingSemesterUsesTheExisting503Contract() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getHome(1L))
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"));

        verify(service).getHome(1L);
    }

    @Test
    void repositoryFailureUsesTheCommon500ContractWithoutLeakingDetails() throws Exception {
        authenticate(Role.MEMBER);
        when(service.getHome(1L)).thenThrow(new IllegalStateException("storage detail"));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        verify(service).getHome(1L);
    }

    @Test
    void controllerRejectsAMissingPrincipalWhenInvokedDirectly() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> new HomeController(service).getHome(null));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    private HomeResponse fullHome(Role role) {
        return new HomeResponse(
                SERVER_TIME,
                new HomeResponse.MemberInfo("상우", 17, role),
                new HomeResponse.SessionInfo(SESSION_ID, "정규 세션", SERVER_TIME.minusHours(1),
                        HomeSessionStatus.IN_PROGRESS, HomeAttendanceStatus.NOT_CHECKED_IN,
                        HomeCheckInStatus.AVAILABLE, null),
                new HomeResponse.Statistics("2026-2H", 67, 8, 12),
                List.of(new HomeResponse.NoticePreview(NOTICE_ID, "최신 공지", SERVER_TIME.minusDays(1))),
                List.of(new ScheduleSummary(SCHEDULE_ID, ScheduleType.SESSION, "다음 세션",
                        SERVER_TIME.plusDays(1), "강의실")));
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("home-reader", "hash", "상우", 17, role)));
    }

    private String accessToken() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
