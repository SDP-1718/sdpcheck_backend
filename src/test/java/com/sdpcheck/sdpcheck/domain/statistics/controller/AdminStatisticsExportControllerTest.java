package com.sdpcheck.sdpcheck.domain.statistics.controller;

import com.sdpcheck.sdpcheck.domain.attendance.exception.AttendanceErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.statistics.dto.response.AttendanceStatisticsExportFile;
import com.sdpcheck.sdpcheck.domain.statistics.enums.MemberStatisticsSort;
import com.sdpcheck.sdpcheck.domain.statistics.service.StatisticsExportService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminStatisticsExportController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AdminStatisticsExportControllerTest {
    private static final String PATH = "/api/v1/admin/statistics/export";
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean StatisticsExportService service;

    @Test
    void adminDownloadsTheDefaultAscendingWorkbookWithTheRequiredHeaders() throws Exception {
        authenticate(Role.ADMIN);
        AttendanceStatisticsExportFile file = exportFile();
        when(service.exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC)).thenReturn(file);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"sdpcheck_attendance_2026-2H_20261029.xlsx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(content().bytes(file.content()));

        verify(service).exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @ParameterizedTest
    @EnumSource(MemberStatisticsSort.class)
    void eachSupportedSortIsBoundAndForwarded(MemberStatisticsSort sort) throws Exception {
        authenticate(Role.ADMIN);
        when(service.exportAttendanceStatistics(sort)).thenReturn(exportFile());

        mvc.perform(get(PATH).queryParam("sort", sort.name())
                        .header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(XLSX));

        verify(service).exportAttendanceStatistics(sort);
    }

    @Test
    void anUnknownSortProducesTheExistingJsonValidationErrorBeforeServiceAccess() throws Exception {
        authenticate(Role.ADMIN);

        mvc.perform(get(PATH).queryParam("sort", "NOT_A_SORT")
                        .header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void memberCannotDownloadStatisticsAndTheServiceIsNotCalled() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void anAdminTokenUsesTheCurrentDatabaseRoleAndCannotSurviveADemotion() throws Exception {
        authenticate(Role.ADMIN);
        String oldAdminToken = accessToken();
        authenticate(Role.MEMBER);

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, oldAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(service);
    }

    @Test
    void anonymousInvalidExpiredAndRefreshTokensAreRejectedBeforeServiceAccess() throws Exception {
        mvc.perform(get(PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

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
    void aDeletedMemberCannotDownloadWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void missingOperatingSemesterProducesJsonRatherThanAnAttachment() throws Exception {
        authenticate(Role.ADMIN);
        when(service.exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(new BusinessException(AttendanceErrorCode.OPERATING_SEMESTER_NOT_CONFIGURED));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("OPERATING_SEMESTER_NOT_CONFIGURED"));

        verify(service).exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @Test
    void workbookGenerationFailureUsesTheCommonJsonInternalErrorContract() throws Exception {
        authenticate(Role.ADMIN);
        when(service.exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC))
                .thenThrow(new BusinessException(CommonErrorCode.INTERNAL_ERROR));

        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, accessToken()))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        verify(service).exportAttendanceStatistics(MemberStatisticsSort.ATTENDANCE_RATE_ASC);
    }

    @Test
    void controllerRejectsANullPrincipalWhenInvokedDirectly() {
        AdminStatisticsExportController controller = new AdminStatisticsExportController(service);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.exportAttendanceStatistics(null, MemberStatisticsSort.ATTENDANCE_RATE_ASC));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    private AttendanceStatisticsExportFile exportFile() {
        return new AttendanceStatisticsExportFile("sdpcheck_attendance_2026-2H_20261029.xlsx",
                new byte[] {80, 75, 3, 4});
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("statistics-export-admin", "hash", "관리자", 17, role)));
    }

    private String accessToken() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
