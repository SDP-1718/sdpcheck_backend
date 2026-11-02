package com.sdpcheck.sdpcheck.domain.member.service;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRoleLockRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import com.sdpcheck.sdpcheck.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminMemberCommandServiceTest {
    private static final long ACTOR_ID = 1L;
    private static final long TARGET_ID = 2L;

    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberRoleLockRepository roleLock = mock(MemberRoleLockRepository.class);
    private final AdminMemberCommandService service = new AdminMemberCommandService(members, roleLock);

    @Test
    void promotesAnotherCompletedMember() {
        Member actor = member(ACTOR_ID, Role.ADMIN);
        Member target = member(TARGET_ID, Role.MEMBER);
        stubActor(actor);
        when(members.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));

        var result = service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN);

        assertEquals(TARGET_ID, result.memberId());
        assertEquals(Role.ADMIN, result.role());
        assertEquals(Role.ADMIN, target.getRole());
        verify(members, never()).countByRoleAndOnboardingCompletedTrue(Role.ADMIN);
    }

    @Test
    void demotesAnotherAdminWhenAnotherAdminRemains() {
        Member actor = member(ACTOR_ID, Role.ADMIN);
        Member target = member(TARGET_ID, Role.ADMIN);
        stubActor(actor);
        when(members.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));
        when(members.countByRoleAndOnboardingCompletedTrue(Role.ADMIN)).thenReturn(2L);

        var result = service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.MEMBER);

        assertEquals(Role.MEMBER, result.role());
        assertEquals(Role.MEMBER, target.getRole());
        verify(members).countByRoleAndOnboardingCompletedTrue(Role.ADMIN);
    }

    @Test
    void acceptsNoOpForAnotherMemberWithoutCountingAdmins() {
        Member actor = member(ACTOR_ID, Role.ADMIN);
        Member target = member(TARGET_ID, Role.MEMBER);
        stubActor(actor);
        when(members.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));

        var result = service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.MEMBER);

        assertEquals(Role.MEMBER, result.role());
        assertEquals(Role.MEMBER, target.getRole());
        verify(members, never()).countByRoleAndOnboardingCompletedTrue(Role.ADMIN);
    }

    @Test
    void acquiresGlobalRoleLockBeforeReadingActorFromDatabase() {
        Member actor = member(ACTOR_ID, Role.ADMIN);
        Member target = member(TARGET_ID, Role.MEMBER);
        stubActor(actor);
        when(members.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(target));

        service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN);

        InOrder order = inOrder(roleLock, members);
        order.verify(roleLock).lockRoleChanges();
        order.verify(members).findByIdForUpdate(ACTOR_ID);
        order.verify(members).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void deniesStaleActorWhoseCurrentDatabaseRoleIsMemberBeforeTargetLookup() {
        when(members.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(member(ACTOR_ID, Role.MEMBER)));

        assertError(AuthErrorCode.ACCESS_DENIED,
                () -> service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN));

        verify(roleLock).lockRoleChanges();
        verify(members).findByIdForUpdate(ACTOR_ID);
        verify(members, never()).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void deniesMissingStaleActorBeforeTargetLookup() {
        when(members.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.empty());

        assertError(AuthErrorCode.ACCESS_DENIED,
                () -> service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN));

        verify(roleLock).lockRoleChanges();
        verify(members).findByIdForUpdate(ACTOR_ID);
        verify(members, never()).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void deniesIncompleteActorBeforeTargetLookup() {
        when(members.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(incompleteMember(ACTOR_ID)));

        assertError(AuthErrorCode.ACCESS_DENIED,
                () -> service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN));

        verify(roleLock).lockRoleChanges();
        verify(members).findByIdForUpdate(ACTOR_ID);
        verify(members, never()).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void missingTargetReturnsNotFoundAfterCurrentActorValidation() {
        stubActor(member(ACTOR_ID, Role.ADMIN));
        when(members.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.empty());

        assertError(MemberErrorCode.MEMBER_NOT_FOUND,
                () -> service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN));

        verify(members).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void incompleteTargetReturnsConflictBeforeRoleCount() {
        stubActor(member(ACTOR_ID, Role.ADMIN));
        when(members.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.of(incompleteMember(TARGET_ID)));

        BusinessException exception = assertError(MemberErrorCode.MEMBER_ONBOARDING_INCOMPLETE,
                () -> service.updateMemberRole(ACTOR_ID, TARGET_ID, Role.ADMIN));

        assertSame(HttpStatus.CONFLICT, exception.getErrorCode().getHttpStatus());
        verify(members, never()).countByRoleAndOnboardingCompletedTrue(Role.ADMIN);
    }

    @Test
    void rejectsSelfRoleChangeEvenWhenRequestedRoleIsAlreadyCurrent() {
        Member actor = member(ACTOR_ID, Role.ADMIN);
        when(members.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(actor));

        BusinessException exception = assertError(MemberErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED,
                () -> service.updateMemberRole(ACTOR_ID, ACTOR_ID, Role.ADMIN));

        assertSame(HttpStatus.CONFLICT, exception.getErrorCode().getHttpStatus());
        assertEquals(Role.ADMIN, actor.getRole());
    }

    @Test
    void rejectsSoleAdminSelfDemotionAsLastAdminBeforeSelfChangeRule() {
        Member actor = member(ACTOR_ID, Role.ADMIN);
        when(members.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(actor));
        when(members.countByRoleAndOnboardingCompletedTrue(Role.ADMIN)).thenReturn(1L);

        BusinessException exception = assertError(MemberErrorCode.LAST_ADMIN_REMOVAL_NOT_ALLOWED,
                () -> service.updateMemberRole(ACTOR_ID, ACTOR_ID, Role.MEMBER));

        assertSame(HttpStatus.CONFLICT, exception.getErrorCode().getHttpStatus());
        assertEquals(Role.ADMIN, actor.getRole());
        verify(members).countByRoleAndOnboardingCompletedTrue(Role.ADMIN);
    }

    @Test
    void validatesNullActorBeforeAcquiringTheRoleLock() {
        assertError(AuthErrorCode.AUTHENTICATION_REQUIRED,
                () -> service.updateMemberRole(null, TARGET_ID, Role.ADMIN));

        verifyNoInteractions(roleLock, members);
    }

    @Test
    void validatesInvalidTargetOrRoleBeforeAcquiringTheRoleLock() {
        assertError(CommonErrorCode.VALIDATION_ERROR,
                () -> service.updateMemberRole(ACTOR_ID, null, Role.ADMIN));
        assertError(CommonErrorCode.VALIDATION_ERROR,
                () -> service.updateMemberRole(ACTOR_ID, 0L, Role.ADMIN));
        assertError(CommonErrorCode.VALIDATION_ERROR,
                () -> service.updateMemberRole(ACTOR_ID, TARGET_ID, null));

        verifyNoInteractions(roleLock, members);
    }

    private void stubActor(Member actor) {
        when(members.findByIdForUpdate(ACTOR_ID)).thenReturn(Optional.of(actor));
    }

    private static Member member(long id, Role role) {
        Member member = new Member("member-" + id, "hash", "회원 " + id, 17, role);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static Member incompleteMember(long id) {
        Member member = Member.createKakaoMember(10_000L + id);
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }

    private static BusinessException assertError(ErrorCode expected, Executable executable) {
        BusinessException exception = assertThrows(BusinessException.class, executable);
        assertSame(expected, exception.getErrorCode());
        return exception;
    }
}
