package com.sdpcheck.sdpcheck.domain.member.service;

import com.sdpcheck.sdpcheck.domain.auth.exception.AuthErrorCode;
import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberRoleResponse;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRoleLockRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import com.sdpcheck.sdpcheck.global.exception.CommonErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminMemberCommandService {
    private final MemberRepository members;
    private final MemberRoleLockRepository roleLock;

    public AdminMemberCommandService(MemberRepository members, MemberRoleLockRepository roleLock) {
        this.members = members;
        this.roleLock = roleLock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MemberRoleResponse updateMemberRole(Long actorId, Long memberId, Role role) {
        if (actorId == null) {
            throw new BusinessException(AuthErrorCode.AUTHENTICATION_REQUIRED);
        }
        if (memberId == null || memberId <= 0 || role == null) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR);
        }

        roleLock.lockRoleChanges();
        Member actor = members.findByIdForUpdate(actorId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ACCESS_DENIED));
        if (!actor.isOnboardingCompleted() || actor.getRole() != Role.ADMIN) {
            throw new BusinessException(AuthErrorCode.ACCESS_DENIED);
        }
        Member target = members.findByIdForUpdate(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
        if (!target.isOnboardingCompleted() || target.getRole() == null) {
            throw new BusinessException(MemberErrorCode.MEMBER_ONBOARDING_INCOMPLETE);
        }
        if (target.getRole() == Role.ADMIN && role == Role.MEMBER
                && members.countByRoleAndOnboardingCompletedTrue(Role.ADMIN) <= 1) {
            throw new BusinessException(MemberErrorCode.LAST_ADMIN_REMOVAL_NOT_ALLOWED);
        }
        if (actorId.equals(memberId)) {
            throw new BusinessException(MemberErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }
        if (target.getRole() != role) {
            target.changeRole(role);
        }
        return new MemberRoleResponse(target.getId(), target.getName(), target.getGeneration(), target.getRole());
    }
}
