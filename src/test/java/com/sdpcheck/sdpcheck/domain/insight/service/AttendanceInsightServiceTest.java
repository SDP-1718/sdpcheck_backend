package com.sdpcheck.sdpcheck.domain.insight.service;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.insight.client.OpenAiClient;
import com.sdpcheck.sdpcheck.domain.insight.dto.AttendanceSummary;
import com.sdpcheck.sdpcheck.domain.insight.repository.AttendanceInsightQueryRepository;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceInsightServiceTest {
    @Mock MemberRepository members;
    @Mock AttendanceInsightQueryRepository summaries;
    @Mock OpenAiClient openAiClient;
    @InjectMocks AttendanceInsightService service;

    private final AttendanceSummary summary = new AttendanceSummary(
            4, 2, 1, 1, 75.0, List.of(AttendanceStatus.PRESENT));

    @Test
    void missingMemberReturnsDomainErrorBeforeSummaryOrAiLookup() {
        assertThatThrownBy(() -> service.getAttendanceInsight(1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
        verifyNoInteractions(summaries, openAiClient);
    }

    @Test
    void noSessionsReturnsDefaultWithoutCallingAi() {
        when(members.existsById(1L)).thenReturn(true);
        when(summaries.findSummary(1L)).thenReturn(new AttendanceSummary(0, 0, 0, 0, 0, List.of()));

        assertThat(service.getAttendanceInsight(1L))
                .isEqualTo("아직 출석 기록이 없어요. 첫 세션부터 함께 시작해 봐요!");
        verifyNoInteractions(openAiClient);
    }

    @Test
    void returnsGeneratedInsightForCurrentMemberSummary() {
        when(members.existsById(1L)).thenReturn(true);
        when(summaries.findSummary(1L)).thenReturn(summary);
        when(openAiClient.generateInsight(summary)).thenReturn(Optional.of("최근 세션에 출석하셨어요!"));

        assertThat(service.getAttendanceInsight(1L)).isEqualTo("최근 세션에 출석하셨어요!");
        verify(summaries).findSummary(1L);
    }

    @Test
    void unavailableAiReturnsFactualDefault() {
        when(members.existsById(1L)).thenReturn(true);
        when(summaries.findSummary(1L)).thenReturn(summary);
        when(openAiClient.generateInsight(summary)).thenReturn(Optional.empty());

        assertThat(service.getAttendanceInsight(1L))
                .isEqualTo("총 4번의 세션에서 출석 2회, 지각 1회, 결석 1회예요.");
    }
}
