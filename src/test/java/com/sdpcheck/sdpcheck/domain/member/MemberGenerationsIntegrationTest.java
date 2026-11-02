package com.sdpcheck.sdpcheck.domain.member;

import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {
        "spring.config.import=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(AdminMemberQueryService.class)
@Testcontainers
class MemberGenerationsIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

    @Autowired MemberRepository members;
    @Autowired AdminMemberQueryService service;

    @Test
    void returnsDistinctPositiveGenerationsInDescendingOrderIncludingAdminOnlyGenerations() {
        members.saveAllAndFlush(List.of(
                member("old-member", 17, Role.MEMBER),
                member("admin-only-generation", 19, Role.ADMIN),
                member("member", 18, Role.MEMBER),
                member("duplicate", 18, Role.ADMIN),
                member("zero", 0, Role.MEMBER),
                member("negative", -1, Role.MEMBER),
                member("missing-generation", null, Role.MEMBER),
                member("missing-role", 22, null),
                Member.createKakaoMember(123L)));
        long count = members.count();

        assertEquals(List.of(19, 18, 17), service.getMemberGenerations().generations());
        assertEquals(List.of(19, 18, 17), service.getMemberGenerations().generations());
        assertEquals(count, members.count());
    }

    @Test
    void returnsAnEmptyListWithoutMembersOrAnOperatingSemester() {
        assertTrue(service.getMemberGenerations().generations().isEmpty());
    }

    @Test
    void addsTheGenerationWhenAKakaoMemberCompletesOnboarding() {
        var member = members.saveAndFlush(Member.createKakaoMember(456L));
        assertTrue(service.getMemberGenerations().generations().isEmpty());

        member.completeOnboarding("신규 회원", 20, Role.MEMBER);
        members.flush();

        assertEquals(List.of(20), service.getMemberGenerations().generations());
    }

    @Test
    void keepsAGenerationWhileEitherAMemberOrAnAdminRemains() {
        var member = members.saveAndFlush(member("member", 19, Role.MEMBER));
        var admin = members.saveAndFlush(member("admin", 19, Role.ADMIN));
        assertEquals(List.of(19), service.getMemberGenerations().generations());

        members.delete(member);
        members.flush();
        assertEquals(List.of(19), service.getMemberGenerations().generations());

        members.delete(admin);
        members.flush();
        assertTrue(service.getMemberGenerations().generations().isEmpty());
    }

    private Member member(String loginId, Integer generation, Role role) {
        return new Member(loginId, "test-password-hash", "회원", generation, role);
    }
}
