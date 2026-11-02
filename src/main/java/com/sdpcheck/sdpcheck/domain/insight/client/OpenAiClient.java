package com.sdpcheck.sdpcheck.domain.insight.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.sdpcheck.sdpcheck.domain.insight.config.OpenAiProperties;
import com.sdpcheck.sdpcheck.domain.insight.dto.AttendanceSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class OpenAiClient {

    private static final String RESPONSES_URL = "https://api.openai.com/v1/responses";
    private static final String INSTRUCTIONS = """
            회원의 출석 통계를 바탕으로 마이페이지에 표시할 한국어 인사이트를 작성하세요.
            제공된 수치와 최근 출석 상태만 근거로 사용하고 원인이나 미래를 추측하지 마세요.
            지각은 출석률에 포함되어 있습니다. PRESENT는 출석, LATE는 지각, ABSENT는 결석입니다.
            최근 상태는 최신순이며, 추세를 설명할 때는 시간 순서를 주의하세요.
            특징 하나를 골라 부드러운 존댓말로 1~2문장, 120자 이내로 작성하세요.
            제목, 목록, 마크다운 없이 사용자에게 보여줄 문구만 반환하세요.
            """;

    private final RestClient restClient;
    private final OpenAiProperties properties;

    @Autowired
    public OpenAiClient(RestClient.Builder builder, OpenAiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = builder.clone().requestFactory(requestFactory).build();
        this.properties = properties;
    }

    OpenAiClient(RestClient restClient, OpenAiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    public Optional<String> generateInsight(AttendanceSummary summary) {
        if (!properties.enabled()
                || !StringUtils.hasText(properties.apiKey())
                || !StringUtils.hasText(properties.model())
                || summary.totalCount() == 0) {
            return Optional.empty();
        }

        String input = String.format(Locale.ROOT, """
                전체 세션 수: %d
                출석 횟수: %d
                지각 횟수: %d
                결석 횟수: %d
                출석률(지각 포함): %.1f%%
                최근 최대 4개 세션의 상태(최신순): %s
                """, summary.totalCount(), summary.presentCount(), summary.lateCount(),
                summary.absentCount(), summary.attendanceRate(), summary.recentStatuses());

        try {
            OpenAiResponse response = restClient.post()
                    .uri(RESPONSES_URL)
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.model(),
                            "instructions", INSTRUCTIONS,
                            "input", input,
                            "max_output_tokens", 1024,
                            "store", false
                    ))
                    .retrieve()
                    .body(OpenAiResponse.class);

            return extractInsight(response);
        } catch (RestClientException exception) {
            // 예외 메시지에는 외부 응답 본문이 포함될 수 있으므로 종류만 기록한다.
            log.warn("OpenAI 출석 인사이트 호출 실패: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<String> extractInsight(OpenAiResponse response) {
        if (response == null || !"completed".equals(response.status()) || response.output() == null) {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder();
        for (OutputItem item : response.output()) {
            if (item == null || !"message".equals(item.type())
                    || !"assistant".equals(item.role()) || item.content() == null) {
                continue;
            }
            for (OutputContent content : item.content()) {
                if (content != null && "refusal".equals(content.type())) {
                    return Optional.empty();
                }
                if (content != null && "output_text".equals(content.type())
                        && StringUtils.hasText(content.text())) {
                    if (!text.isEmpty()) {
                        text.append(' ');
                    }
                    text.append(content.text().strip());
                }
            }
        }

        String insight = text.toString().strip();
        if (!StringUtils.hasText(insight) || insight.length() > 200) {
            return Optional.empty();
        }
        return Optional.of(insight);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OpenAiResponse(String status, List<OutputItem> output) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputItem(String type, String role, List<OutputContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OutputContent(String type, String text) {
    }
}
