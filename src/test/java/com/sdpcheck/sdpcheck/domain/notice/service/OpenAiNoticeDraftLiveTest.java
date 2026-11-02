package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.insight.config.OpenAiProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.web.client.RestClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "NOTICE_AI_LIVE_TEST", matches = "true")
class OpenAiNoticeDraftLiveTest {
    @Test void generatesRealDraftWithLocalConfiguration() throws Exception {
        var env = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".env"), StandardCharsets.UTF_8)) { env.load(reader); }
        var client = new OpenAiNoticeDraftClient(RestClient.builder(), new OpenAiProperties(
                Boolean.parseBoolean(env.getProperty("OPENAI_ENABLED")), env.getProperty("OPENAI_API_KEY"), env.getProperty("OPENAI_MODEL")));
        var draft = client.generate("11월 7일 오후 7시 동아리 정기 세션. 장소는 학생회관 201호. 노트북 지참. 이 메모만 공지로 정리해 줘.");
        assertEquals("AI", draft.mode().name());
        assertFalse(draft.title().isBlank());
        assertTrue(draft.content().contains("201"));
        // Keys, input and generated text are deliberately not logged.
    }
}
