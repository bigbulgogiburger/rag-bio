package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiHydeQueryTransformer implements HydeQueryTransformer {

    private static final Logger log = LoggerFactory.getLogger(OpenAiHydeQueryTransformer.class);

    private static final String SYSTEM_PROMPT = """
            당신은 Bio-Rad 기술지원 전문가입니다.
            다음 질문에 대해 기술 매뉴얼에 있을 법한 답변을 3-5문장으로 작성하세요.
            구체적인 수치, 절차, 조건을 포함하세요. 추측이어도 괜찮습니다.""";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final String chatModel;

    public OpenAiHydeQueryTransformer(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat:gpt-4o-mini}") String chatModel,
            ObjectMapper objectMapper,
            EmbeddingService embeddingService) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
    }

    // visible-for-testing
    OpenAiHydeQueryTransformer(RestClient restClient, ObjectMapper objectMapper,
                               EmbeddingService embeddingService, String chatModel) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
    }

    @Override
    public List<Double> transformAndEmbed(String question, String productContext) {
        try {
            String hypotheticalAnswer = generateHypotheticalAnswer(question, productContext);
            log.info("hyde.transform question='{}' hypo_length={}", question, hypotheticalAnswer.length());
            return embeddingService.embedDocument(hypotheticalAnswer);
        } catch (Exception ex) {
            log.warn("hyde.transform.failed -> falling back to query embedding: {}", ex.getMessage());
            return embeddingService.embedQuery(question);
        }
    }

    private String generateHypotheticalAnswer(String question, String productContext) {
        String userMessage = productContext != null && !productContext.isBlank()
                ? "[제품: " + productContext + "] " + question
                : question;

        String response = restClient.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", chatModel,
                        "messages", List.of(
                                Map.of("role", "system", "content", SYSTEM_PROMPT),
                                Map.of("role", "user", "content", userMessage)
                        ),
                        "max_tokens", 500,
                        "temperature", 0.7
                ))
                .retrieve()
                .body(String.class);

        JsonNode root;
        try {
            root = objectMapper.readTree(response == null ? "{}" : response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse HyDE LLM response", e);
        }
        String content = root.path("choices").path(0).path("message").path("content").asText("");

        if (content.isBlank()) {
            throw new IllegalStateException("Empty HyDE response from LLM");
        }
        return content.trim();
    }
}
