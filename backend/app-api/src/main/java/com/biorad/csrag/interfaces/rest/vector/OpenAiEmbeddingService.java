package com.biorad.csrag.interfaces.rest.vector;

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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String embeddingModel;
    private final MockEmbeddingService fallback;

    public OpenAiEmbeddingService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.embedding:text-embedding-3-small}") String embeddingModel,
            ObjectMapper objectMapper,
            MockEmbeddingService fallback
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.embeddingModel = embeddingModel;
        this.fallback = fallback;
    }

    @Override
    public List<Double> embed(String text) {
        String input = text == null ? "" : text;
        try {
            String response = restClient.post()
                    .uri("/embeddings")
                    .body(Map.of(
                            "model", embeddingModel,
                            "input", input
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            JsonNode embeddingNode = root.path("data").path(0).path("embedding");
            if (!embeddingNode.isArray() || embeddingNode.isEmpty()) {
                throw new IllegalStateException("openai embedding response has no vector data");
            }

            List<Double> vector = new ArrayList<>(embeddingNode.size());
            for (JsonNode value : embeddingNode) {
                vector.add(value.asDouble());
            }
            return vector;
        } catch (Exception ex) {
            log.warn("openai.embedding.failed -> fallback to mock: {}", ex.getMessage());
            return fallback.embed(input);
        }
    }
}
