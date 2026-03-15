package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.application.ops.RagMetricsService;
import com.biorad.csrag.infrastructure.openai.OpenAiRequestUtils;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

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
    private final RagMetricsService ragMetricsService;
    private final PromptRegistry promptRegistry;

    @Autowired
    public OpenAiAdaptiveRetrievalAgent(
            HybridSearchService hybridSearchService,
            RerankingService rerankingService,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-medium:gpt-4.1-mini}") String chatModel,
            ObjectMapper objectMapper,
            RagMetricsService ragMetricsService,
            PromptRegistry promptRegistry
    ) {
        this(hybridSearchService, rerankingService,
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                objectMapper, chatModel, ragMetricsService, promptRegistry);
    }

    /** 테스트용 생성자 — RestClient를 직접 주입 */
    OpenAiAdaptiveRetrievalAgent(HybridSearchService hybridSearchService,
                                  RerankingService rerankingService,
                                  RestClient restClient,
                                  ObjectMapper objectMapper,
                                  String chatModel,
                                  RagMetricsService ragMetricsService) {
        this(hybridSearchService, rerankingService, restClient, objectMapper, chatModel, ragMetricsService, null);
    }

    OpenAiAdaptiveRetrievalAgent(HybridSearchService hybridSearchService,
                                  RerankingService rerankingService,
                                  RestClient restClient,
                                  ObjectMapper objectMapper,
                                  String chatModel,
                                  RagMetricsService ragMetricsService,
                                  PromptRegistry promptRegistry) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.ragMetricsService = ragMetricsService;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public AdaptiveResult retrieve(String question, String productContext, UUID inquiryId) {
        SearchFilter filter = SearchFilter.forInquiry(inquiryId);

        // 1차 시도: 원본 쿼리로 검색
        List<HybridSearchResult> candidates = hybridSearchService.search(question, 50, filter);
        List<RerankingService.RerankResult> bestResults = rerankingService.rerank(question, candidates, 10);
        double bestScore = bestResults.isEmpty() ? 0.0
                : bestResults.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);

        log.info("adaptive.retrieve attempt=1 query={} topScore={} inquiryId={}",
                question, String.format("%.3f", bestScore), inquiryId);

        if (bestScore >= MIN_CONFIDENCE) {
            if (ragMetricsService != null) ragMetricsService.record(null, "ADAPTIVE_RETRY", 0);
            return AdaptiveResult.success(bestResults, 1);
        }

        // 2차 시도: Unified LLM call로 3개 변형 쿼리를 한 번에 생성 후 검색
        try {
            List<ReformulatedQuery> variants = generateAllVariants(question, productContext);

            // Unified 응답 파싱 실패 시 (빈 variants) 순차 폴백
            if (variants.isEmpty()) {
                log.warn("adaptive.unified: no variants parsed, falling back to sequential");
                return fallbackSequentialRetrieval(question, productContext, inquiryId, filter, bestResults, bestScore);
            }

            log.info("Adaptive retrieval: generated {} variants in 1 LLM call (was 3 sequential calls)", variants.size());

            List<RerankingService.RerankResult> allResults = new ArrayList<>(bestResults);

            for (ReformulatedQuery variant : variants) {
                List<HybridSearchResult> variantCandidates = hybridSearchService.search(variant.query(), 50, filter);
                List<RerankingService.RerankResult> variantResults = rerankingService.rerank(variant.query(), variantCandidates, 10);

                double topScore = variantResults.isEmpty() ? 0.0
                        : variantResults.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);

                log.info("adaptive.retrieve unified strategy={} query={} topScore={} inquiryId={}",
                        variant.strategy(), variant.query(), String.format("%.3f", topScore), inquiryId);

                allResults.addAll(variantResults);
            }

            // chunkId 기준 중복 제거 (최고 점수 유지)
            allResults = deduplicateByChunkId(allResults);
            // 점수 내림차순 정렬
            allResults.sort(Comparator.comparingDouble(RerankingService.RerankResult::rerankScore).reversed());

            // 상위 10개로 제한
            if (allResults.size() > 10) {
                allResults = new ArrayList<>(allResults.subList(0, 10));
            }

            double unifiedBestScore = allResults.isEmpty() ? 0.0
                    : allResults.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);

            if (ragMetricsService != null) ragMetricsService.record(null, "ADAPTIVE_RETRY", 1);

            if (unifiedBestScore >= MIN_CONFIDENCE) {
                return AdaptiveResult.success(allResults, 2);
            }
            return allResults.isEmpty()
                    ? AdaptiveResult.noEvidence(question)
                    : AdaptiveResult.lowConfidence(allResults, unifiedBestScore);

        } catch (Exception e) {
            log.warn("adaptive.unified.failed, falling back to sequential: {}", e.getMessage());
            return fallbackSequentialRetrieval(question, productContext, inquiryId, filter, bestResults, bestScore);
        }
    }

    /**
     * Unified LLM call: 1번의 LLM 호출로 3가지 전략의 쿼리를 동시에 생성.
     * JSON 모드를 사용하여 구조화된 응답을 보장.
     */
    List<ReformulatedQuery> generateAllVariants(String query, String productContext) {
        String prompt;
        if (promptRegistry != null) {
            prompt = promptRegistry.get("adaptive-search-unified", Map.of(
                    "query", query,
                    "productContext", productContext != null ? productContext : ""
            ));
        } else {
            prompt = String.format("""
                    You are a search query reformulation expert for Bio-Rad technical documents.
                    Given a search query that returned insufficient results, generate 3 alternative queries.

                    Return a JSON array with exactly 3 objects:
                    [
                      {"strategy": "expand", "query": "expanded query"},
                      {"strategy": "broaden", "query": "broadened query"},
                      {"strategy": "translate", "query": "translated query"}
                    ]

                    Original query: %s
                    Product context: %s""", query, productContext != null ? productContext : "");
        }

        Map<String, Object> body = OpenAiRequestUtils.chatBodyWithJsonMode(
                chatModel,
                List.of(Map.of("role", "user", "content", prompt)),
                300, 0.3
        );

        String json = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);

        return parseVariants(json);
    }

    /**
     * OpenAI JSON 응답에서 변형 쿼리 목록을 파싱.
     */
    List<ReformulatedQuery> parseVariants(String responseJson) {
        List<ReformulatedQuery> variants = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            String content = root.at("/choices/0/message/content").asText().trim();

            JsonNode array = objectMapper.readTree(content);
            if (array.isArray()) {
                for (JsonNode item : array) {
                    String strategy = item.has("strategy") ? item.get("strategy").asText() : "unknown";
                    String query = item.has("query") ? item.get("query").asText() : "";
                    if (!query.isBlank()) {
                        variants.add(new ReformulatedQuery(strategy, query));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("adaptive.parseVariants.failed: {}", e.getMessage());
        }
        return variants;
    }

    /**
     * chunkId 기준 중복 제거. 동일 chunkId가 여러 번 나타나면 최고 rerankScore를 가진 것만 유지.
     */
    List<RerankingService.RerankResult> deduplicateByChunkId(List<RerankingService.RerankResult> results) {
        Map<UUID, RerankingService.RerankResult> bestByChunk = new LinkedHashMap<>();
        for (RerankingService.RerankResult result : results) {
            bestByChunk.merge(result.chunkId(), result,
                    (existing, incoming) -> incoming.rerankScore() > existing.rerankScore() ? incoming : existing);
        }
        return new ArrayList<>(bestByChunk.values());
    }

    /**
     * Unified call 실패 시 기존 순차 방식으로 폴백.
     */
    private AdaptiveResult fallbackSequentialRetrieval(String question, String productContext,
                                                        UUID inquiryId, SearchFilter filter,
                                                        List<RerankingService.RerankResult> bestResults,
                                                        double bestScore) {
        String currentQuery = question;

        for (int attempt = 1; attempt < MAX_RETRIES; attempt++) {
            currentQuery = reformulateQuery(question, currentQuery, bestResults, attempt - 1);

            List<HybridSearchResult> candidates = hybridSearchService.search(currentQuery, 50, filter);
            List<RerankingService.RerankResult> results = rerankingService.rerank(currentQuery, candidates, 10);

            double topScore = results.isEmpty() ? 0.0
                    : results.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);

            if (topScore > bestScore) {
                bestScore = topScore;
                bestResults = results;
            }

            log.info("adaptive.retrieve.fallback attempt={} query={} topScore={} inquiryId={}",
                    attempt + 1, currentQuery, String.format("%.3f", topScore), inquiryId);

            if (topScore >= MIN_CONFIDENCE) {
                if (ragMetricsService != null) ragMetricsService.record(null, "ADAPTIVE_RETRY", attempt);
                return AdaptiveResult.success(bestResults, attempt + 1);
            }
        }

        if (ragMetricsService != null) ragMetricsService.record(null, "ADAPTIVE_RETRY", MAX_RETRIES);
        return bestResults.isEmpty()
                ? AdaptiveResult.noEvidence(question)
                : AdaptiveResult.lowConfidence(bestResults, bestScore);
    }

    /**
     * 쿼리 재구성 전략 (폴백용):
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
                case 0 -> promptRegistry != null
                        ? promptRegistry.get("adaptive-search-expand", Map.of("original", original, "excerpts", excerpts))
                        : String.format("""
                        다음 검색 쿼리의 동의어와 관련어를 포함하여 쿼리를 확장하세요.
                        원본 쿼리: %s
                        검색 결과 일부: %s
                        확장된 쿼리만 반환하세요 (한 줄):""", original, excerpts);
                case 1 -> promptRegistry != null
                        ? promptRegistry.get("adaptive-search-broaden", Map.of("original", original))
                        : String.format("""
                        다음 검색 쿼리를 더 상위 개념/포괄적 용어로 재구성하세요.
                        원본 쿼리: %s
                        상위 개념 쿼리만 반환하세요 (한 줄):""", original);
                default -> promptRegistry != null
                        ? promptRegistry.get("adaptive-search-translate", Map.of("original", original))
                        : String.format("""
                        다음 한국어 쿼리를 영어로 번역하세요. Bio-Rad 기술 용어를 정확히 번역하세요.
                        쿼리: %s
                        영어 번역만 반환하세요 (한 줄):""", original);
            };

            Map<String, Object> body = OpenAiRequestUtils.chatBody(
                    chatModel,
                    List.of(Map.of("role", "user", "content", prompt)),
                    100, 0.3
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

    /**
     * 변형 쿼리 레코드.
     */
    record ReformulatedQuery(String strategy, String query) {}
}
