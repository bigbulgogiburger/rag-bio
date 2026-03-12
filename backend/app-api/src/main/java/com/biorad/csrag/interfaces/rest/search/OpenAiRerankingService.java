package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.application.ops.RagMetricsService;
import com.biorad.csrag.infrastructure.openai.OpenAiRequestUtils;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRerankingService.class);

    private static final int MAX_CONTENT_LENGTH = 1000;
    private static final int LISTWISE_MAX_CONTENT_LENGTH = 500;
    private static final int LISTWISE_MAX_CANDIDATES = 20;

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

    private static final String LISTWISE_PROMPT = """
            You are a Bio-Rad technical support expert. Rank the following document passages by relevance to the query.

            Query: %s

            Documents:
            %s

            Respond with ONLY a JSON array ranking each document:
            [{"index": 0, "score": 0.95}, {"index": 1, "score": 0.3}, ...]
            Score 0.0-1.0 where 1.0 = directly answers the query, 0.0 = completely irrelevant.
            You MUST include ALL document indices in your response.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final MockRerankingService fallback;
    private final RagMetricsService ragMetricsService;
    private final PromptRegistry promptRegistry;

    @Autowired
    public OpenAiRerankingService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-medium:gpt-4.1-mini}") String chatModel,
            ObjectMapper objectMapper,
            MockRerankingService fallback,
            RagMetricsService ragMetricsService,
            PromptRegistry promptRegistry
    ) {
        this(
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                chatModel, objectMapper, fallback, ragMetricsService, promptRegistry
        );
    }

    OpenAiRerankingService(RestClient restClient, String chatModel, ObjectMapper objectMapper,
                           MockRerankingService fallback, RagMetricsService ragMetricsService) {
        this(restClient, chatModel, objectMapper, fallback, ragMetricsService, null);
    }

    OpenAiRerankingService(RestClient restClient, String chatModel, ObjectMapper objectMapper,
                           MockRerankingService fallback, RagMetricsService ragMetricsService,
                           PromptRegistry promptRegistry) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.fallback = fallback;
        this.ragMetricsService = ragMetricsService;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        try {
            List<RerankResult> scored = listwise(query, candidates);

            List<RerankResult> result = scored.stream()
                    .sorted(Comparator.comparingDouble(RerankResult::rerankScore).reversed())
                    .limit(topK)
                    .toList();

            log.info("openai.rerank(listwise): scored {} candidates, returning top-{}", candidates.size(), result.size());

            if (!result.isEmpty()) {
                double avgBefore = candidates.stream().mapToDouble(HybridSearchResult::fusedScore).average().orElse(0.0);
                double avgAfter = result.stream().mapToDouble(RerankResult::rerankScore).average().orElse(0.0);
                if (ragMetricsService != null) ragMetricsService.record(null, "RERANK_IMPROVEMENT", avgAfter - avgBefore);
            }
            return result;
        } catch (Exception ex) {
            log.warn("openai.rerank.listwise.failed -> fallback to mock: {}", ex.getMessage());
            return fallback.rerank(query, candidates, topK);
        }
    }

    private List<RerankResult> listwise(String query, List<HybridSearchResult> candidates) throws Exception {
        int maxCandidates = Math.min(candidates.size(), LISTWISE_MAX_CANDIDATES);
        List<HybridSearchResult> subset = candidates.subList(0, maxCandidates);

        StringBuilder documentList = new StringBuilder();
        for (int i = 0; i < subset.size(); i++) {
            String content = subset.get(i).content();
            String truncated = content.length() > LISTWISE_MAX_CONTENT_LENGTH
                    ? content.substring(0, LISTWISE_MAX_CONTENT_LENGTH) + "..."
                    : content;
            documentList.append("[").append(i).append("] ").append(truncated).append("\n\n");
        }

        String prompt = (promptRegistry != null)
                ? promptRegistry.get("reranking-listwise", Map.of("query", query, "documents", documentList.toString()))
                : String.format(LISTWISE_PROMPT, query, documentList);

        Map<String, Object> body = OpenAiRequestUtils.chatBodyWithJsonMode(
                chatModel,
                List.of(Map.of("role", "user", "content", prompt)),
                2048, 0.0
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        String json = stripCodeFences(content.strip());

        // Parse as array of {index, score} — JSON Mode guarantees valid JSON
        JsonNode rankings = objectMapper.readTree(json);
        // Handle both array and object with "rankings" key (JSON Mode may wrap in object)
        if (rankings.has("rankings")) {
            rankings = rankings.get("rankings");
        }

        Map<Integer, Double> scoreMap = new HashMap<>();
        if (rankings.isArray()) {
            for (JsonNode item : rankings) {
                int idx = item.path("index").asInt(-1);
                double score = item.path("score").asDouble(0.0);
                if (idx >= 0 && idx < subset.size()) {
                    scoreMap.put(idx, score);
                }
            }
        }

        List<RerankResult> results = new ArrayList<>();
        for (int i = 0; i < subset.size(); i++) {
            HybridSearchResult c = subset.get(i);
            double rerankScore = scoreMap.getOrDefault(i, c.fusedScore());
            results.add(new RerankResult(
                    c.chunkId(), c.documentId(), c.content(),
                    c.fusedScore(), rerankScore,
                    c.sourceType(), c.matchSource()
            ));
        }
        return results;
    }

    private double scorePair(String query, String content) throws Exception {
        String truncated = content.length() > MAX_CONTENT_LENGTH
                ? content.substring(0, MAX_CONTENT_LENGTH) + "..."
                : content;

        Map<String, Object> body = OpenAiRequestUtils.chatBody(
                chatModel,
                List.of(
                        Map.of("role", "user", "content", promptRegistry != null
                                ? promptRegistry.get("reranking-pair", Map.of("query", query, "document", truncated))
                                : String.format(RERANK_PROMPT, query, truncated))
                ),
                100, 0.0
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
