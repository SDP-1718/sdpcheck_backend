package com.sdpcheck.sdpcheck.domain.member.controller;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.dto.request.MemberRoleUpdateRequest;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberRoleResponse;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.GlobalExceptionHandler;
import com.sdpcheck.sdpcheck.global.security.config.SecurityConfig;
import com.sdpcheck.sdpcheck.global.security.jwt.AuthenticatedMember;
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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminMemberController.class, properties = {
        "spring.config.import=",
        "jwt.secret=test-only-secret-key-for-ci-0123456789-abcdefghij",
        "jwt.access-token-expiration=60000",
        "jwt.refresh-token-expiration=120000"
})
@Import({SecurityConfig.class, JwtProvider.class, SecurityExceptionHandler.class, GlobalExceptionHandler.class})
class AdminMemberRoleControllerTest {
    private static final String PATH = "/api/v1/admin/members/{memberId}/role";

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean MemberRepository members;
    @MockitoBean AdminMemberQueryService queryService;
    @MockitoBean AdminMemberCommandService commandService;

    @Test
    void adminUpdatesAnotherMembersRoleInTheCommonEnvelopeWithoutCaching() throws Exception {
        authenticate(Role.ADMIN);
        var response = new MemberRoleResponse(2L, "대상 회원", 18, Role.ADMIN);
        when(commandService.updateMemberRole(1L, 2L, Role.ADMIN)).thenReturn(response);

        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store, private"))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.message").value("요청을 성공적으로 처리했습니다."))
                .andExpect(jsonPath("$.result", aMapWithSize(4)))
                .andExpect(jsonPath("$.result.memberId").value(2))
                .andExpect(jsonPath("$.result.name").value("대상 회원"))
                .andExpect(jsonPath("$.result.generation").value(18))
                .andExpect(jsonPath("$.result.role").value("ADMIN"));

        verify(commandService).updateMemberRole(1L, 2L, Role.ADMIN);
    }

    @Test
    void sameRoleForAnotherMemberIsASuccessfulNoOpAtTheApiBoundary() throws Exception {
        authenticate(Role.ADMIN);
        when(commandService.updateMemberRole(1L, 2L, Role.MEMBER))
                .thenReturn(new MemberRoleResponse(2L, "기존 부원", 17, Role.MEMBER));

        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.role").value("MEMBER"));

        verify(commandService).updateMemberRole(1L, 2L, Role.MEMBER);
    }

    @Test
    void memberCannotChangeRolesOrElevateWithTheRequestBody() throws Exception {
        authenticate(Role.MEMBER);

        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(commandService);
    }

    @Test
    void anonymousAndInvalidTokensAreRejectedBeforeTheCommand() throws Exception {
        mvc.perform(patch(PATH, 2L).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(members, queryService, commandService);

        var expired = new JwtProvider("test-only-secret-key-for-ci-0123456789-abcdefghij", -10_000, 120_000);
        for (String authorization : List.of("Bearer broken.token",
                "Bearer " + expired.createAccessToken(1L),
                "Bearer " + jwtProvider.createRefreshToken(1L))) {
            mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, authorization)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
        }
        verifyNoInteractions(members, queryService, commandService);
    }

    @Test
    void incompleteKakaoActorCannotChangeRoles() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(Member.createKakaoMember(12345L)));

        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verifyNoInteractions(commandService);
    }

    @Test
    void sameAccessTokenLosesAdminAccessAfterTheDatabaseRoleChanges() throws Exception {
        when(members.findById(1L)).thenReturn(Optional.of(member(Role.ADMIN)), Optional.of(member(Role.MEMBER)));
        when(commandService.updateMemberRole(1L, 2L, Role.ADMIN))
                .thenReturn(new MemberRoleResponse(2L, "대상 회원", 18, Role.ADMIN));
        String authorization = token();

        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(commandService, times(1)).updateMemberRole(1L, 2L, Role.ADMIN);
    }

    @Test
    void nullUnknownAndOrdinalRoleValuesAreValidationErrorsBeforeTheCommand() throws Exception {
        authenticate(Role.ADMIN);

        for (String body : List.of("{}", "{\"role\":null}", "{\"role\":\"OWNER\"}",
                "{\"role\":0}", "{\"role\":\"0\"}")) {
            mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(commandService);
    }

    @Test
    void malformedZeroAndNegativeMemberIdsAreValidationErrorsBeforeTheCommand() throws Exception {
        authenticate(Role.ADMIN);

        for (String memberId : List.of("not-a-number", "0", "-1")) {
            mvc.perform(patch(PATH, memberId).header(HttpHeaders.AUTHORIZATION, token())
                            .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        verifyNoInteractions(commandService);
    }

    @Test
    void commandDomainErrorsUseTheirExactStatusAndCode() throws Exception {
        authenticate(Role.ADMIN);

        doThrow(new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND))
                .when(commandService).updateMemberRole(1L, 2L, Role.ADMIN);
        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));

        doThrow(new BusinessException(MemberErrorCode.MEMBER_ONBOARDING_INCOMPLETE))
                .when(commandService).updateMemberRole(1L, 2L, Role.ADMIN);
        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MEMBER_ONBOARDING_INCOMPLETE"));

        doThrow(new BusinessException(MemberErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED))
                .when(commandService).updateMemberRole(1L, 1L, Role.MEMBER);
        mvc.perform(patch(PATH, 1L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ROLE_CHANGE_NOT_ALLOWED"));

        doThrow(new BusinessException(MemberErrorCode.LAST_ADMIN_REMOVAL_NOT_ALLOWED))
                .when(commandService).updateMemberRole(1L, 2L, Role.MEMBER);
        mvc.perform(patch(PATH, 2L).header(HttpHeaders.AUTHORIZATION, token())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"MEMBER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LAST_ADMIN_REMOVAL_NOT_ALLOWED"));
    }

    @Test
    void directControllerCallsRejectMissingAndNonAdminPrincipals() {
        var controller = new AdminMemberController(queryService, commandService);
        var request = new MemberRoleUpdateRequest(Role.ADMIN);

        var missing = assertThrows(BusinessException.class,
                () -> controller.updateMemberRole(2L, request, null));
        assertEquals(AuthErrorCode.AUTHENTICATION_REQUIRED, missing.getErrorCode());

        for (AuthenticatedMember principal : List.of(
                new AuthenticatedMember(1L, "reader", Role.MEMBER),
                new AuthenticatedMember(1L, null, null))) {
            var denied = assertThrows(BusinessException.class,
                    () -> controller.updateMemberRole(2L, request, principal));
            assertEquals(AuthErrorCode.ACCESS_DENIED, denied.getErrorCode());
        }
        verifyNoInteractions(commandService);
    }

    private void authenticate(Role role) {
        when(members.findById(1L)).thenReturn(Optional.of(member(role)));
    }

    private Member member(Role role) {
        return new Member("role-admin", "hash", "관리자", 17, role);
    }

    private String token() {
        return "Bearer " + jwtProvider.createAccessToken(1L);
    }
}
