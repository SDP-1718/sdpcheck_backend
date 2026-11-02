package com.sdpcheck.sdpcheck.domain.notice.service;

import com.sdpcheck.sdpcheck.domain.insight.config.OpenAiProperties;
import com.sdpcheck.sdpcheck.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.junit.jupiter.api.Assertions.*;

class OpenAiNoticeDraftClientTest {
    @Test void sendsMemoAndStrictSchemaAndParsesAiDraft() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new OpenAiNoticeDraftClient(builder.build(), new OpenAiProperties(true, "test-key", "test-model"));
        server.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.input").value("Friday 7pm session"))
                .andExpect(jsonPath("$.store").value(false))
                .andExpect(jsonPath("$.text.format.strict").value(true))
                .andRespond(withSuccess("""
                        {"status":"completed","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"{\\"title\\":\\"Session\\",\\"content\\":\\"Friday 7pm\\"}"}]}]}
                        """, MediaType.APPLICATION_JSON));
        var result = client.generate("Friday 7pm session");
        assertEquals("Session", result.title());
        assertEquals("AI", result.mode().name());
        server.verify();
    }

    @Test void rejectsIncompleteRefusedMalformedAndEmptyOutputs() {
        for (String response : new String[]{"{}", "{\"status\":\"incomplete\",\"output\":[]}",
                "{\"status\":\"completed\",\"output\":[]}", "not-json",
                "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"refusal\"}]}]}"}) {
            var builder = RestClient.builder();
            var server = MockRestServiceServer.bindTo(builder).build();
            var client = new OpenAiNoticeDraftClient(builder.build(), new OpenAiProperties(true, "key", "model"));
            server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertThrows(BusinessException.class, () -> client.generate("memo"));
            server.verify();
        }
    }

    @Test void missingKeyDoesNotCallUpstream() {
        var client = new OpenAiNoticeDraftClient(RestClient.create(), new OpenAiProperties(true, "", "model"));
        assertFalse(client.available());
        assertThrows(BusinessException.class, () -> client.generate("memo"));
    }
}
