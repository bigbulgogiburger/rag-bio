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

import java.util.*;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiAdaptiveRetrievalAgent implements AdaptiveRetrievalAgent {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdaptiveRetrievalAgent.class);
    private static final int MAX_RETRIES = 3;
    private static final double MIN_CONFIDENCE = 0.50;

    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;

    public OpenAiAdaptiveRetrievalAgent(
            HybridSearchService hybridSearchService,
            RerankingService rerankingService,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper
    ) {
        this(hybridSearchService, rerankingService,
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                objectMapper, chatModel);
    }

    /** 테스트용 생성자 — RestClient를 직접 주입 */
    OpenAiAdaptiveRetrievalAgent(HybridSearchService hybridSearchService,
                                  RerankingService rerankingService,
                                  RestClient restClient,
                                  ObjectMapper objectMapper,
                                  String chatModel) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    @Override
    public AdaptiveResult retrieve(String question, String productContext, UUID inquiryId) {
        String currentQuery = question;
        List<RerankingService.RerankResult> bestResults = List.of();
        double bestScore = 0.0;

        SearchFilter filter = SearchFilter.forInquiry(inquiryId);

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            List<HybridSearchResult> candidates = hybridSearchService.search(currentQuery, 50, filter);
            List<RerankingService.RerankResult> results = rerankingService.rerank(currentQuery, candidates, 10);

            double topScore = results.isEmpty() ? 0.0
                    : results.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);

            if (topScore > bestScore) {
                bestScore = topScore;
                bestResults = results;
            }

            log.info("adaptive.retrieve attempt={} query={} topScore={} inquiryId={}",
                    attempt + 1, currentQuery, String.format("%.3f", topScore), inquiryId);

            if (topScore >= MIN_CONFIDENCE) {
                return AdaptiveResult.success(bestResults, attempt + 1);
            }

            if (attempt < MAX_RETRIES - 1) {
                currentQuery = reformulateQuery(question, currentQuery, results, attempt);
            }
        }

        return bestResults.isEmpty()
                ? AdaptiveResult.noEvidence(question)
                : AdaptiveResult.lowConfidence(bestResults, bestScore);
    }

    /**
     * 쿼리 재구성 전략:
     *   1차: 동의어/관련어 확장
     *   2차: 상위 개념으로 확장
     *   3차: 영어/한국어 교차 검색
     */
    private String reformulateQuery(String original, String current,
                                     List<RerankingService.RerankResult> results, int attempt) {
        try {
            String excerpts = results.stream()
                    .limit(3)
                    .map(r -> r.content().substring(0, Math.min(200, r.content().length())))
                    .reduce("", (a, b) -> a + "\n" + b);

            String prompt = switch (attempt) {
                case 0 -> String.format("""
                        다음 검색 쿼리의 동의어와 관련어를 포함하여 쿼리를 확장하세요.
                        원본 쿼리: %s
                        검색 결과 일부: %s
                        확장된 쿼리만 반환하세요 (한 줄):""", original, excerpts);
                case 1 -> String.format("""
                        다음 검색 쿼리를 더 상위 개념/포괄적 용어로 재구성하세요.
                        원본 쿼리: %s
                        상위 개념 쿼리만 반환하세요 (한 줄):""", original);
                default -> String.format("""
                        다음 한국어 쿼리를 영어로 번역하세요. Bio-Rad 기술 용어를 정확히 번역하세요.
                        쿼리: %s
                        영어 번역만 반환하세요 (한 줄):""", original);
            };

            Map<String, Object> body = Map.of(
                    "model", chatModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 100,
                    "temperature", 0.3
            );

            String json = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(json);
            String reformulated = node.at("/choices/0/message/content").asText().trim();
            log.info("adaptive.reformulate attempt={} original={} reformulated={}", attempt, original, reformulated);
            return reformulated.isBlank() ? current : reformulated;
        } catch (Exception e) {
            log.warn("adaptive.reformulate.failed attempt={} error={}", attempt, e.getMessage());
            return current;
        }
    }
}
