package com.sdpcheck.sdpcheck.domain.statistics.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionAttendanceStatisticsItem(
        @Schema(description = "세션 ID") UUID sessionId,
        @Schema(description = "세션명", example = "정규 세션") String sessionTitle,
        @Schema(description = "예정 시작 일시 (Asia/Seoul)", example = "2026-10-08T19:00:00+09:00")
        OffsetDateTime startsAt,
        @Schema(description = "해당 세션에 등록된 전체 출석 대상 인원", example = "12") long totalCount,
        @Schema(description = "정상 출석 인원 (지각 제외)", example = "7") long presentCount,
        @Schema(description = "지각 인원", example = "1") long lateCount,
        @Schema(description = "결석 인원 (출석 기록이 없는 대상자 포함)", example = "4") long absentCount,
        @Schema(description = "(정상 출석 + 지각) / 대상 인원 × 100, 정수 반올림. 대상 0명이면 null",
                example = "67", nullable = true) Integer attendanceRate
) {
}
