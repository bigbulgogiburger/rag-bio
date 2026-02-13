package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiComposeStep implements ComposeStep {

    private static final Logger log = LoggerFactory.getLogger(OpenAiComposeStep.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final DefaultComposeStep fallback;

    public OpenAiComposeStep(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper,
            DefaultComposeStep fallback
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.fallback = fallback;
    }

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel) {
        try {
            String prompt = buildPrompt(analysis, tone, channel);

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", chatModel,
                            "messages", new Object[]{
                                    Map.of("role", "system", "content", "너는 Bio-Rad CS 한국어 답변 도우미다. 과장하지 말고 근거 기반으로 답하라."),
                                    Map.of("role", "user", "content", prompt)
                            },
                            "temperature", 0.2
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("openai compose empty content");
            }

            return new ComposeStepResult(content, fallback.execute(analysis, tone, channel).formatWarnings());
        } catch (Exception ex) {
            log.warn("openai.compose.failed -> fallback to default compose: {}", ex.getMessage());
            return fallback.execute(analysis, tone, channel);
        }
    }

    private String buildPrompt(AnalyzeResponse analysis, String tone, String channel) {
        return "아래 분석 결과를 바탕으로 고객 답변 초안을 한국어로 작성해줘.\n"
                + "- tone: " + (tone == null ? "professional" : tone) + "\n"
                + "- channel: " + (channel == null ? "email" : channel) + "\n"
                + "- verdict: " + analysis.verdict() + "\n"
                + "- confidence: " + analysis.confidence() + "\n"
                + "- riskFlags: " + analysis.riskFlags() + "\n"
                + "- reason: " + analysis.reason() + "\n"
                + "요구사항:\n"
                + "1) 과장/단정 금지\n"
                + "2) 후속 확인 항목 1~3개 포함\n"
                + "3) channel=email이면 인사/마무리 포함, messenger면 간결하게\n";
    }
}
