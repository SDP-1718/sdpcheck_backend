package com.sdpcheck.sdpcheck.domain.session.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SessionSemesterRepository {

	private final NamedParameterJdbcTemplate jdbc;

	public SessionSemesterRepository(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<CurrentSemester> findCurrent() {
		return jdbc.getJdbcTemplate().query(
				"SELECT id, ends_on FROM semesters WHERE is_current = true",
				(rs, rowNum) -> new CurrentSemester(
						rs.getObject("id", UUID.class),
						rs.getObject("ends_on", LocalDate.class)
				)
		).stream().findFirst();
	}

	public record CurrentSemester(UUID id, LocalDate endsOn) {
	}
}
