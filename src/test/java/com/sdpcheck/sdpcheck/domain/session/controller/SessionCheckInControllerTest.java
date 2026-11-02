package com.sdpcheck.sdpcheck.domain.session.controller;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.attendance.exception.CheckInErrorCode;
import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.session.dto.request.CheckInReqDTO;
import com.sdpcheck.sdpcheck.domain.session.dto.response.CheckInResDTO;
import com.sdpcheck.sdpcheck.domain.session.service.SessionCheckInService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import com.sdpcheck.sdpcheck.global.security.jwt.SecurityExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = SessionCheckInController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class SessionCheckInControllerTest {
    private static final UUID SESSION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String PATH = "/api/v1/sessions/" + SESSION_ID + "/check-ins";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean SessionCheckInService service;

    @ParameterizedTest
    @EnumSource(Role.class)
    void memberAndAdminCreateOnlyTheirOwnCheckIn(Role role) throws Exception {
        authenticate(role);
        CheckInResDTO response = response();
        when(service.checkIn(SESSION_ID, 1L, new CheckInReqDTO("qr-token"))).thenReturn(response);

        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"qrToken\":\"qr-token\",\"memberId\":2,\"status\":\"LATE\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        allOf(containsString("private"), containsString("no-store"))))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("CREATED"))
                .andExpect(jsonPath("$.result", aMapWithSize(4)))
                .andExpect(jsonPath("$.result.checkInId").value(71))
                .andExpect(jsonPath("$.result.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.result.attendanceStatus").value("PRESENT"))
                .andExpect(jsonPath("$.result.checkedInAt").value("2026-10-29T09:00:00+09:00"));

        verify(service).checkIn(SESSION_ID, 1L, new CheckInReqDTO("qr-token"));
        verify(service, never()).checkIn(SESSION_ID, 2L, new CheckInReqDTO("qr-token"));
    }

    @Test
    void missingBlankOrTooLongQrTokensAreRejectedBeforeServiceAccess() throws Exception {
        authenticate(Role.MEMBER);

        for (String body : new String[] {
                "{}",
                "{\"qrToken\":\" \"}",
                "{\"qrToken\":\"%s\"}".formatted("x".repeat(4097))
        }) {
            mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, accessToken())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, accessToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void aMalformedSessionPathIsRejectedBeforeServiceAccess() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(post("/api/v1/sessions/not-a-uuid/check-ins").header(HttpHeaders.AUTHORIZATION, accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"qr-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @MethodSource("checkInErrors")
    void domainCheckInErrorsUseTheExistingJsonEnvelope(ErrorCode errorCode, int expectedStatus) throws Exception {
        authenticate(Role.MEMBER);
        when(service.checkIn(SESSION_ID, 1L, new CheckInReqDTO("qr-token")))
                .thenThrow(new BusinessException(errorCode));

        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"qr-token\"}"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.result").value((Object) null));

        verify(service).checkIn(SESSION_ID, 1L, new CheckInReqDTO("qr-token"));
    }

    @Test
    void anonymousInvalidExpiredAndRefreshTokensCannotCreateCheckIns() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"qr-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : new String[] {
                "Bearer broken.token",
                "Bearer " + expired.createAccessToken(1L),
                "Bearer " + jwtProvider.createRefreshToken(1L)
        }) {
            mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"qr-token\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }

        verifyNoInteractions(service);
    }

    @Test
    void aDeletedMemberCannotCreateCheckInsWithAnOldValidToken() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.empty());

        mvc.perform(post(PATH).header(HttpHeaders.AUTHORIZATION, accessToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qrToken\":\"qr-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        verifyNoInteractions(service);
    }

    @Test
    void controllerRejectsANullPrincipalWhenInvokedDirectly() {
        SessionCheckInController controller = new SessionCheckInController(service);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.createCheckIn(null, SESSION_ID, new CheckInReqDTO("qr-token")));

        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(service);
    }

    private static Stream<Arguments> checkInErrors() {
        return Stream.of(
                Arguments.of(CheckInErrorCode.INVALID_QR_TOKEN, 400),
                Arguments.of(CheckInErrorCode.QR_TOKEN_EXPIRED, 410),
                Arguments.of(CheckInErrorCode.SESSION_NOT_FOUND, 404),
                Arguments.of(CheckInErrorCode.SESSION_NOT_IN_PROGRESS, 409),
                Arguments.of(CheckInErrorCode.ALREADY_CHECKED_IN, 409),
                Arguments.of(AuthErrorCode.ACCESS_DENIED, 403));
    }

    private CheckInResDTO response() {
        return new CheckInResDTO(71L, SESSION_ID, AttendanceStatus.PRESENT,
                OffsetDateTime.parse("2026-10-29T09:00:00+09:00"));
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(
                new Member("session-check-in", "hash", "회원", 17, role)));
    }

    private String accessToken() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
