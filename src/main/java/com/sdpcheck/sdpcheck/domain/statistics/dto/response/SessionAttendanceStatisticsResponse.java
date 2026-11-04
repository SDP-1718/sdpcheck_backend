package com.sdpcheck.sdpcheck.domain.statistics.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record SessionAttendanceStatisticsResponse(
        @Schema(description = "현재 운영 학기 코드", example = "2026-2H") String periodCode,
        @Schema(description = "종료된 정규 세션 목록. 예정 시작 일시·세션 ID 오름차순, 없으면 빈 배열")
        List<SessionAttendanceStatisticsItem> sessions
) {
    public SessionAttendanceStatisticsResponse {
        sessions = List.copyOf(sessions);
    }
}
