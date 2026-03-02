package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.application.ops.RagMetricsService;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
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

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final EmbeddingService embeddingService;
    private final String chatModel;
    private final RagMetricsService ragMetricsService;
    private final PromptRegistry promptRegistry;

    public OpenAiHydeQueryTransformer(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-light:gpt-4.1-mini}") String chatModel,
            ObjectMapper objectMapper,
            EmbeddingService embeddingService,
            RagMetricsService ragMetricsService,
            PromptRegistry promptRegistry) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
        this.ragMetricsService = ragMetricsService;
        this.promptRegistry = promptRegistry;
    }

    // visible-for-testing
    OpenAiHydeQueryTransformer(RestClient restClient, ObjectMapper objectMapper,
                               EmbeddingService embeddingService, String chatModel,
                               PromptRegistry promptRegistry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.embeddingService = embeddingService;
        this.chatModel = chatModel;
        this.ragMetricsService = null;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public List<Double> transformAndEmbed(String question, String productContext) {
        try {
            String hypotheticalAnswer = generateHypotheticalAnswer(question, productContext);
            log.info("hyde.transform question='{}' hypo_length={}", question, hypotheticalAnswer.length());
            if (ragMetricsService != null) ragMetricsService.record(null, "HYDE_USAGE", 1.0);
            return embeddingService.embedDocument(hypotheticalAnswer);
        } catch (Exception ex) {
            log.warn("hyde.transform.failed -> falling back to query embedding: {}", ex.getMessage());
            if (ragMetricsService != null) ragMetricsService.record(null, "HYDE_USAGE", 0.0);
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
                                Map.of("role", "system", "content", promptRegistry.get("hyde-system")),
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
