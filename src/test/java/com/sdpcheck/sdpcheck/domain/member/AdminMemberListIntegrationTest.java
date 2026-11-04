package com.sdpcheck.sdpcheck.domain.member;

import com.sdpcheck.sdpcheck.domain.member.dto.response.AdminMemberListItem;
import com.sdpcheck.sdpcheck.domain.member.entity.Member;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberCommandService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberListQueryService;
import com.sdpcheck.sdpcheck.domain.member.service.AdminMemberQueryService;
import com.sdpcheck.sdpcheck.global.security.jwt.JwtProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.config.import=")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional
class AdminMemberListIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(
            DockerImageName.parse("postgres:17-alpine"));

    @Autowired AdminMemberListQueryService service;
    @Autowired AdminMemberQueryService generations;
    @Autowired AdminMemberCommandService command;
    @Autowired MemberRepository members;
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProvider jwt;
    @Autowired MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE TABLE members, semesters RESTART IDENTITY CASCADE");
        entityManager.clear();
    }

    @Test
    void matchesTheGenerationDropdownScopeWithoutAnOperatingSemester() {
        long adminOnly = member("admin-only", "김관리", 19, Role.ADMIN);
        long member = member("member", "이회원", 18, Role.MEMBER);
        long admin = member("admin", "박임원", 18, Role.ADMIN);
        member("zero", "제외", 0, Role.MEMBER);
        member("negative", "제외", -1, Role.ADMIN);
        Member pending = members.saveAndFlush(Member.createKakaoMember(123L));
        jdbc.update("UPDATE members SET generation = 20, name = '미완료' WHERE id = ?", pending.getId());

        var response = service.getAdminMembers(null, 0, 20);

        assertEquals(List.of(19, 18), generations.getMemberGenerations().generations());
        assertEquals(List.of(adminOnly, admin, member),
                response.members().stream().map(AdminMemberListItem::memberId).toList());
        assertEquals(List.of(19, 18), response.members().stream().map(AdminMemberListItem::generation).distinct().toList());
        assertFalse(response.hasNext());
        assertEquals(0L, jdbc.queryForObject("SELECT count(*) FROM semesters", Long.class));
    }

    @Test
    void filtersTheSelectedGenerationAndIncludesBothMemberAndAdminRoles() {
        member("old", "가", 17, Role.MEMBER);
        long member = member("selected-member", "가", 18, Role.MEMBER);
        long admin = member("selected-admin", "나", 18, Role.ADMIN);
        long adminOnly = member("new-admin", "가", 19, Role.ADMIN);

        var selected = service.getAdminMembers(18, 0, 20);
        var adminsGeneration = service.getAdminMembers(19, 0, 20);

        assertEquals(List.of(member, admin), selected.members().stream().map(AdminMemberListItem::memberId).toList());
        assertEquals(List.of(Role.MEMBER, Role.ADMIN), selected.members().stream().map(AdminMemberListItem::role).toList());
        assertEquals(List.of(adminOnly), adminsGeneration.members().stream().map(AdminMemberListItem::memberId).toList());
    }

    @Test
    void pagesInGenerationNameAndIdOrderWithoutOverlappingRows() {
        long last = member("old", "가", 17, Role.MEMBER);
        long third = member("name-last", "나", 18, Role.ADMIN);
        long first = member("same-name-first", "가", 18, Role.MEMBER);
        long second = member("same-name-second", "가", 18, Role.ADMIN);

        var page0 = service.getAdminMembers(null, 0, 2);
        var page1 = service.getAdminMembers(null, 1, 2);
        var page2 = service.getAdminMembers(null, 2, 2);

        assertEquals(List.of(first, second), page0.members().stream().map(AdminMemberListItem::memberId).toList());
        assertTrue(page0.hasNext());
        assertEquals(List.of(third, last), page1.members().stream().map(AdminMemberListItem::memberId).toList());
        assertFalse(page1.hasNext());
        assertTrue(page2.members().isEmpty());
        assertFalse(page2.hasNext());
        assertEquals(1, page1.page());
        assertEquals(2, page1.size());
    }

    @Test
    void returnsEmptyForNoMembersUnknownGenerationsAndHugePages() {
        assertTrue(service.getAdminMembers(null, 0, 20).members().isEmpty());
        member("member", "회원", 18, Role.MEMBER);

        var missingGeneration = service.getAdminMembers(999, 0, 20);
        var hugePage = service.getAdminMembers(18, Integer.MAX_VALUE, 50);

        assertTrue(missingGeneration.members().isEmpty());
        assertFalse(missingGeneration.hasNext());
        assertTrue(hugePage.members().isEmpty());
        assertFalse(hugePage.hasNext());
        assertEquals(Integer.MAX_VALUE, hugePage.page());
    }

    @Test
    void doesNotRestrictTheListToSemesterMembersOrModifyMemberRows() {
        long included = member("outside-roster", "회원", 17, Role.MEMBER);
        long rosterMember = member("in-roster", "운영진", 18, Role.ADMIN);
        UUID semester = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO semesters (id, name, starts_on, ends_on, is_current)
                VALUES (?, '운영 학기', DATE '2026-07-01', DATE '2026-12-31', true)
                """, semester);
        jdbc.update("INSERT INTO semester_members (semester_id, member_id) VALUES (?, ?)", semester, rosterMember);
        var rowsBefore = jdbc.queryForList("SELECT id, role, name, generation, xmin::text AS version FROM members ORDER BY id");

        var response = service.getAdminMembers(null, 0, 20);

        assertEquals(List.of(rosterMember, included), response.members().stream().map(AdminMemberListItem::memberId).toList());
        assertEquals(rowsBefore, jdbc.queryForList("SELECT id, role, name, generation, xmin::text AS version FROM members ORDER BY id"));
        assertEquals(1L, jdbc.queryForObject("SELECT count(*) FROM semester_members", Long.class));
    }

    @Test
    void returnsTheUpdatedRoleAfterGrantAndRevoke() {
        long admin = member("actor", "관리자", 19, Role.ADMIN);
        long target = member("target", "대상", 18, Role.MEMBER);

        assertEquals(Role.MEMBER, service.getAdminMembers(18, 0, 20).members().getFirst().role());
        command.updateMemberRole(admin, target, Role.ADMIN);
        entityManager.flush();
        entityManager.clear();
        assertEquals(Role.ADMIN, service.getAdminMembers(18, 0, 20).members().getFirst().role());
        command.updateMemberRole(admin, target, Role.MEMBER);
        entityManager.flush();
        entityManager.clear();
        assertEquals(Role.MEMBER, service.getAdminMembers(18, 0, 20).members().getFirst().role());
    }

    @Test
    void newlyOnboardedMembersAppearInTheListAndGenerationDropdown() {
        Member pending = members.saveAndFlush(Member.createKakaoMember(456L));
        assertTrue(service.getAdminMembers(20, 0, 20).members().isEmpty());

        pending.completeOnboarding("신규 회원", 20, Role.MEMBER);
        members.flush();

        assertEquals(List.of(20), generations.getMemberGenerations().generations());
        assertEquals(List.of(new AdminMemberListItem(pending.getId(), "신규 회원", 20, Role.MEMBER)),
                service.getAdminMembers(20, 0, 20).members());
    }

    @Test
    void administratorCanReadOnlyTheFourPublicMemberFieldsThroughTheApi() throws Exception {
        long admin = member("actor", "관리자", 19, Role.ADMIN);
        long target = member("private-login", "조회 대상", 18, Role.MEMBER);

        mvc.perform(get("/api/v1/admin/members").param("generation", "18")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.createAccessToken(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.result.members[0]", aMapWithSize(4)))
                .andExpect(jsonPath("$.result.members[0].memberId").value(target))
                .andExpect(jsonPath("$.result.members[0].name").value("조회 대상"))
                .andExpect(jsonPath("$.result.members[0].generation").value(18))
                .andExpect(jsonPath("$.result.members[0].role").value("MEMBER"));
    }

    @Test
    void swaggerPublishesTheListAlongsideGenerationAndRoleOperations() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/admin/members'].get.operationId").value("getAdminMembers"))
                .andExpect(jsonPath("$.paths['/api/v1/admin/members'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/admin/members'].get.parameters.length()").value(3))
                .andExpect(jsonPath("$.paths['/api/v1/admin/members/generations'].get").exists())
                .andExpect(jsonPath("$.paths['/api/v1/admin/members/{memberId}/role'].patch").exists())
                .andExpect(jsonPath("$.components.schemas.AdminMemberListItem.properties", aMapWithSize(4)))
                .andExpect(jsonPath("$.components.schemas.AdminMemberListItem.properties.role.enum.length()").value(2));
    }

    private long member(String loginId, String name, int generation, Role role) {
        return members.saveAndFlush(new Member(loginId, "test-password-hash", name, generation, role)).getId();
    }
}
