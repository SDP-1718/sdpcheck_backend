package com.sdpcheck.sdpcheck.domain.schedule.repository;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ScheduleRepositoryTest {
    @Test
    void listQuerySeparatesWhereClauseFromOrdering() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var repository = new ScheduleRepository(jdbc);

        repository.find(OffsetDateTime.parse("2026-10-01T00:00:00+09:00"),
                OffsetDateTime.parse("2026-11-01T00:00:00+09:00"), null, 1, 20);

        var sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(SqlParameterSource.class), any(RowMapper.class));
        assertTrue(sql.getValue().matches("(?s).*WHERE\\s+1=1.*:to\\s+ORDER BY.*"));
    }
}
