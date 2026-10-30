package com.sdpcheck.sdpcheck.domain.home.dto.response;

import com.sdpcheck.sdpcheck.domain.home.enums.HomeAttendanceStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeCheckInStatus;
import com.sdpcheck.sdpcheck.domain.home.enums.HomeSessionStatus;
import com.sdpcheck.sdpcheck.domain.member.enums.Role;
import com.sdpcheck.sdpcheck.domain.schedule.dto.response.ScheduleSummary;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HomeResponse(
        OffsetDateTime serverTime,
        MemberInfo member,
        SessionInfo session,
        Statistics statistics,
        List<NoticePreview> notices,
        List<ScheduleSummary> upcomingSchedules
) {
    public record MemberInfo(String name, int generation, Role role) {
    }

    public record SessionInfo(
            UUID id,
            String title,
            OffsetDateTime scheduledStartAt,
            HomeSessionStatus status,
            HomeAttendanceStatus attendanceStatus,
            HomeCheckInStatus checkInStatus,
            OffsetDateTime checkedInAt
    ) {
    }

    public record Statistics(
            String periodCode,
            Integer attendanceRate,
            long attendedSessionCount,
            long totalSessionCount
    ) {
    }

    public record NoticePreview(UUID id, String title, OffsetDateTime createdAt) {
    }
}
