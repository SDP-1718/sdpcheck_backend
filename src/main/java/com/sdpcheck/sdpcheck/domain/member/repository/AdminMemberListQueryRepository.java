package com.sdpcheck.sdpcheck.domain.member.repository;

import com.sdpcheck.sdpcheck.domain.member.dto.response.AdminMemberListItem;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AdminMemberListQueryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminMemberListQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AdminMemberListItem> findMembers(Integer generation, int limit, long offset) {
        String sql = """
                SELECT id, name, generation, role
                FROM members
                WHERE onboarding_completed = true AND role IN (:roles) AND generation > 0
                """;
        var params = new MapSqlParameterSource("roles", List.of(Role.MEMBER.name(), Role.ADMIN.name()))
                .addValue("limit", limit).addValue("offset", offset);
        if (generation != null) {
            sql += " AND generation = :generation\n";
            params.addValue("generation", generation);
        }
        sql += "ORDER BY generation DESC, name ASC, id ASC LIMIT :limit OFFSET :offset";
        return jdbc.query(sql, params, (rs, rowNum) -> new AdminMemberListItem(
                rs.getLong("id"), rs.getString("name"), rs.getInt("generation"),
                Role.valueOf(rs.getString("role"))));
    }
}
