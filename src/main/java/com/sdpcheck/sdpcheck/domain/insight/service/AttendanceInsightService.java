package com.sdpcheck.sdpcheck.domain.insight.service;


import com.sdpcheck.sdpcheck.domain.insight.client.OpenAiClient;
import com.sdpcheck.sdpcheck.domain.insight.dto.AttendanceSummary;
import com.sdpcheck.sdpcheck.domain.insight.repository.AttendanceInsightQueryRepository;
import com.sdpcheck.sdpcheck.domain.member.exception.MemberErrorCode;
import com.sdpcheck.sdpcheck.domain.member.repository.MemberRepository;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttendanceInsightService {
    private final MemberRepository memberRepository;
    private final AttendanceInsightQueryRepository attendanceInsightQueryRepository;
    private final OpenAiClient openAiClient;

    public String getAttendanceInsight(Long memberId){
        if (!memberRepository.existsById(memberId)){
            throw new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND);
        }

        AttendanceSummary summary =
                attendanceInsightQueryRepository.findSummary(memberId);

        if (summary.totalCount() == 0) {
            return createDefaultInsight(summary);
        }

        return openAiClient.generateInsight(summary)
                .orElseGet(() -> createDefaultInsight(summary));
    }

    private String createDefaultInsight(AttendanceSummary summary) {
        if(summary.totalCount() == 0){
            return "아직 출석 기록이 없어요. 첫 세션부터 함께 시작해 봐요!";
        }

        return "총 %d번의 세션에서 출석 %d회, 지각 %d회, 결석 %d회예요."
                .formatted(
                        summary.totalCount(),
                        summary.presentCount(),
                        summary.lateCount(),
                        summary.absentCount()
                );
    }
}
