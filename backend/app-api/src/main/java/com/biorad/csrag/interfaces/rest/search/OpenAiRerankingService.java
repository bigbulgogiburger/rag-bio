package com.biorad.csrag.interfaces.rest.search;

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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRerankingService.class);

    private static final int MAX_CONTENT_LENGTH = 1000;

    private static final String RERANK_PROMPT = """
            You are a Bio-Rad technical support expert evaluating document relevance.
            Rate how relevant this document passage is to the given query.

            Query: %s

            Document passage:
            %s

            Respond with ONLY a JSON object: {"score": 0.0 to 1.0, "reason": "brief reason"}
            Score guidelines:
            - 1.0: Directly answers the query with specific details
            - 0.7-0.9: Highly relevant, contains most needed information
            - 0.4-0.6: Partially relevant, some useful context
            - 0.1-0.3: Tangentially related
            - 0.0: Completely irrelevant
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final MockRerankingService fallback;

    public OpenAiRerankingService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper,
            MockRerankingService fallback
    ) {
        this(
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                chatModel, objectMapper, fallback
        );
    }

    OpenAiRerankingService(RestClient restClient, String chatModel, ObjectMapper objectMapper, MockRerankingService fallback) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.fallback = fallback;
    }

    @Override
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topK) {
        try {
            List<RerankResult> scored = new ArrayList<>();

            for (HybridSearchResult candidate : candidates) {
                double rerankScore = scorePair(query, candidate.content());
                scored.add(new RerankResult(
                        candidate.chunkId(),
                        candidate.documentId(),
                        candidate.content(),
                        candidate.fusedScore(),
                        rerankScore,
                        candidate.sourceType(),
                        candidate.matchSource()
                ));
            }

            List<RerankResult> result = scored.stream()
                    .sorted(Comparator.comparingDouble(RerankResult::rerankScore).reversed())
                    .limit(topK)
                    .toList();

            log.info("openai.rerank: scored {} candidates, returning top-{}", candidates.size(), result.size());
            return result;
        } catch (Exception ex) {
            log.warn("openai.rerank.failed -> fallback to mock: {}", ex.getMessage());
            return fallback.rerank(query, candidates, topK);
        }
    }

    private double scorePair(String query, String content) throws Exception {
        String truncated = content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH) + "..."
                : content;

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", List.of(
                        Map.of("role", "user", "content", String.format(RERANK_PROMPT, query, truncated))
                ),
                "max_tokens", 100,
                "temperature", 0.0
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        String responseContent = root.path("choices").path(0).path("message").path("content").asText("");

        String json = stripCodeFences(responseContent.strip());
        JsonNode parsed = objectMapper.readTree(json);
        return parsed.path("score").asDouble(0.5);
    }

    private String stripCodeFences(String text) {
        if (text.startsWith("```")) {
            int nl = text.indexOf('\n');
            int lf = text.lastIndexOf("```");
            if (nl > 0 && lf > nl) {
                return text.substring(nl + 1, lf).strip();
            }
        }
        return text;
    }
}
