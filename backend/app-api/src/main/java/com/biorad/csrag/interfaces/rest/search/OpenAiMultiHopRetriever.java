package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.application.ops.RagMetricsService;
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

import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiMultiHopRetriever implements MultiHopRetriever {

    private static final Logger log = LoggerFactory.getLogger(OpenAiMultiHopRetriever.class);
    private static final int MAX_HOPS = 2;

    private final AdaptiveRetrievalAgent adaptiveAgent;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final RagMetricsService ragMetricsService;
    private final PromptRegistry promptRegistry;

    @Autowired
    public OpenAiMultiHopRetriever(
            AdaptiveRetrievalAgent adaptiveAgent,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-medium:gpt-4.1}") String chatModel,
            ObjectMapper objectMapper,
            RagMetricsService ragMetricsService,
            PromptRegistry promptRegistry
    ) {
        this(adaptiveAgent,
                RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                chatModel, objectMapper, ragMetricsService, promptRegistry);
    }

    /** 테스트용 생성자 — RestClient를 직접 주입 */
    OpenAiMultiHopRetriever(AdaptiveRetrievalAgent adaptiveAgent, RestClient restClient,
                             String chatModel, ObjectMapper objectMapper,
                             RagMetricsService ragMetricsService) {
        this(adaptiveAgent, restClient, chatModel, objectMapper, ragMetricsService, null);
    }

    OpenAiMultiHopRetriever(AdaptiveRetrievalAgent adaptiveAgent, RestClient restClient,
                             String chatModel, ObjectMapper objectMapper,
                             RagMetricsService ragMetricsService, PromptRegistry promptRegistry) {
        this.adaptiveAgent = adaptiveAgent;
        this.restClient = restClient;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.ragMetricsService = ragMetricsService;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public MultiHopResult retrieve(String question, UUID inquiryId) {
        List<HopRecord> hopRecords = new ArrayList<>();

        // Hop 1: 초기 검색
        AdaptiveRetrievalAgent.AdaptiveResult hop1 = adaptiveAgent.retrieve(question, "", inquiryId);
        double hop1Score = hop1.confidence();
        hopRecords.add(new HopRecord(1, question, hop1.evidences().size(), hop1Score));

        log.info("multihop.hop1 question={} results={} score={} inquiryId={}",
                question, hop1.evidences().size(), String.format("%.3f", hop1Score), inquiryId);

        // Hop 2 필요 여부 판단
        HopDecision decision = evaluateNeedForNextHop(question, hop1.evidences());

        if (!decision.needsMoreHops() || hop1.evidences().isEmpty()) {
            return MultiHopResult.singleHop(hop1.evidences());
        }

        // Hop 2: 추출된 엔티티로 2차 검색
        log.info("multihop.hop2 nextQuery={} inquiryId={}", decision.nextQuery(), inquiryId);
        AdaptiveRetrievalAgent.AdaptiveResult hop2 =
                adaptiveAgent.retrieve(decision.nextQuery(), "", inquiryId);
        double hop2Score = hop2.confidence();
        hopRecords.add(new HopRecord(2, decision.nextQuery(), hop2.evidences().size(), hop2Score));

        // 결과 병합 및 중복 제거 (chunkId 기준)
        List<RerankingService.RerankResult> merged = mergeAndDeduplicate(hop1.evidences(), hop2.evidences());

        return MultiHopResult.multiHop(merged, hopRecords);
    }

    private record HopDecision(boolean needsMoreHops, String nextQuery) {}

    /**
     * LLM이 추가 검색 필요 여부를 판단.
     * 1차 결과에서 교차 검증이 필요한 엔티티를 추출.
     */
    private HopDecision evaluateNeedForNextHop(String question, List<RerankingService.RerankResult> hop1Results) {
        if (hop1Results.isEmpty()) {
            return new HopDecision(false, "");
        }

        try {
            String excerpts = hop1Results.stream()
                    .limit(3)
                    .map(r -> r.content().substring(0, Math.min(300, r.content().length())))
                    .collect(Collectors.joining("\n---\n"));

            String prompt = promptRegistry != null
                    ? promptRegistry.get("multihop-system", Map.of("question", question, "excerpts", excerpts))
                    : String.format("""
                다음 질문에 답하기 위해 추가 문서 검색이 필요한지 판단하세요.

                질문: %s

                1차 검색 결과:
                %s

                1차 결과로 질문에 완전히 답할 수 있으면 {"needs_more_hops": false}를 반환하세요.
                추가 검색이 필요하면 {"needs_more_hops": true, "next_query": "구체적인 2차 검색 쿼리"}를 반환하세요.
                JSON만 반환하세요.
                """, question, excerpts);

            Map<String, Object> body = Map.of(
                    "model", chatModel,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "max_tokens", 200,
                    "temperature", 0.1
            );

            String json = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(json);
            String content = node.at("/choices/0/message/content").asText().trim();

            // JSON 코드블록 제거
            content = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();

            JsonNode result = objectMapper.readTree(content);
            boolean needsMore = result.path("needs_more_hops").asBoolean(false);
            String nextQuery = result.path("next_query").asText("");

            return new HopDecision(needsMore && !nextQuery.isBlank(), nextQuery);
        } catch (Exception e) {
            log.warn("multihop.evaluate.failed: {}", e.getMessage());
            return new HopDecision(false, "");
        }
    }

    private List<RerankingService.RerankResult> mergeAndDeduplicate(
            List<RerankingService.RerankResult> hop1,
            List<RerankingService.RerankResult> hop2) {

        LinkedHashMap<UUID, RerankingService.RerankResult> seen = new LinkedHashMap<>();

        // hop1 먼저 (더 관련성 높음)
        for (RerankingService.RerankResult r : hop1) {
            seen.putIfAbsent(r.chunkId(), r);
        }
        // hop2 추가 (중복 제거)
        for (RerankingService.RerankResult r : hop2) {
            seen.putIfAbsent(r.chunkId(), r);
        }

        return new ArrayList<>(seen.values());
    }
}
