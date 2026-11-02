package com.sdpcheck.sdpcheck.domain.insight.client;

import com.sdpcheck.sdpcheck.domain.attendance.enums.AttendanceStatus;
import com.sdpcheck.sdpcheck.domain.insight.config.OpenAiProperties;
import com.sdpcheck.sdpcheck.domain.insight.dto.AttendanceSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiClientTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiProperties properties = new OpenAiProperties(true, "test-key", "test-model");
    private final OpenAiClient client = new OpenAiClient(builder.build(), properties);
    private final AttendanceSummary summary = new AttendanceSummary(
            4, 2, 1, 1, 75.0,
            List.of(AttendanceStatus.PRESENT, AttendanceStatus.LATE,
                    AttendanceStatus.ABSENT, AttendanceStatus.PRESENT));

    @Test
    void sendsOnlyAttendanceSummaryAndReadsTextAfterReasoningItem() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.max_output_tokens").value(1024))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString("75.0%")))
                .andExpect(jsonPath("$.input").value(org.hamcrest.Matchers.containsString(
                        "[PRESENT, LATE, ABSENT, PRESENT]")))
                .andExpect(jsonPath("$.memberId").doesNotExist())
                .andRespond(withSuccess("""
                        {"status":"completed","output":[
                          {"type":"reasoning","summary":[]},
                          {"type":"message","role":"assistant","content":[
                            {"type":"output_text","text":"최근 세션에 출석하셨어요.","annotations":[]},
                            {"type":"output_text","text":"꾸준히 이어가 봐요!"}
                          ]}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generateInsight(summary))
                .contains("최근 세션에 출석하셨어요. 꾸준히 이어가 봐요!");
        server.verify();
    }

    @Test
    void disabledOrMissingConfigurationSkipsHttpRequest() {
        for (OpenAiProperties configuration : List.of(
                new OpenAiProperties(false, "test-key", "test-model"),
                new OpenAiProperties(true, "", "test-model"),
                new OpenAiProperties(true, "test-key", " "))) {
            assertThat(new OpenAiClient(builder.build(), configuration).generateInsight(summary)).isEmpty();
        }
        server.verify();
    }

    @Test
    void noAttendanceSkipsHttpRequest() {
        assertThat(client.generateInsight(new AttendanceSummary(0, 0, 0, 0, 0, List.of()))).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "not-json", "null", "{}",
            "{\"status\":\"completed\",\"output\":[]}",
            "{\"status\":\"completed\",\"output\":[null]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":null}]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\" \"}]}]}",
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}]}",
            "{\"status\":\"incomplete\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"partial\"}]}]}"
    })
    void unusableResponsesReturnEmpty(String body) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThat(client.generateInsight(summary)).isEmpty();
        server.verify();
    }

    @Test
    void excessivelyLongResponseReturnsEmpty() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"type":"message","role":"assistant",
                        "content":[{"type":"output_text","text":"%s"}]}]}
                        """.formatted("가".repeat(201)), MediaType.APPLICATION_JSON));
        assertThat(client.generateInsight(summary)).isEmpty();
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 500, 503})
    void httpErrorsReturnEmptyWithoutRetry(int status) {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.valueOf(status)));
        assertThat(client.generateInsight(summary)).isEmpty();
        server.verify();
    }

    @Test
    void networkTimeoutReturnsEmpty() {
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withException(new SocketTimeoutException("simulated timeout")));
        assertThat(client.generateInsight(summary)).isEmpty();
        server.verify();
    }
}
