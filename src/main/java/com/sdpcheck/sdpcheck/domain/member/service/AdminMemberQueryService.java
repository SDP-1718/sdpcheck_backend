package com.sdpcheck.sdpcheck.domain.member.service;

import com.sdpcheck.sdpcheck.domain.member.dto.response.MemberGenerationsResponse;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class AdminMemberQueryService {
    private final MemberRepository memberRepository;

    public AdminMemberQueryService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public MemberGenerationsResponse getMemberGenerations() {
        return new MemberGenerationsResponse(
                memberRepository.findCompletedMemberGenerations(List.of(Role.MEMBER, Role.ADMIN)));
    }
}
