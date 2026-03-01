package com.biorad.csrag.interfaces.rest.search;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.stream.Collectors;

/**
 * OpenAI Function Calling 기반 Tool-Calling 검색 에이전트.
 * LLM이 질문을 분석하고 적절한 검색 도구를 선택하여 실행한 뒤,
 * 결과를 병합 + 중복 제거 + 리랭킹하여 반환.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiSearchToolAgent implements SearchToolAgent {

    private static final Logger log = LoggerFactory.getLogger(OpenAiSearchToolAgent.class);

    private static final int SEARCH_TOP_K = 20;
    private static final int FINAL_TOP_K = 10;

    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;
    private final ProductExtractorService productExtractorService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;

    public OpenAiSearchToolAgent(
            HybridSearchService hybridSearchService,
            RerankingService rerankingService,
            ProductExtractorService productExtractorService,
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper
    ) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.productExtractorService = productExtractorService;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    // package-private constructor for testing
    OpenAiSearchToolAgent(
            HybridSearchService hybridSearchService,
            RerankingService rerankingService,
            ProductExtractorService productExtractorService,
            RestClient restClient,
            String chatModel,
            ObjectMapper objectMapper
    ) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
        this.productExtractorService = productExtractorService;
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
    }

    @Override
    public List<RerankingService.RerankResult> agenticSearch(String question, UUID inquiryId) {
        try {
            List<ToolCall> toolCalls = selectTools(question, inquiryId);
            log.info("search-tool-agent: LLM selected {} tools for question='{}'",
                    toolCalls.size(), truncate(question, 80));

            List<HybridSearchResult> allResults = new ArrayList<>();
            for (ToolCall call : toolCalls) {
                log.info("search-tool-agent: executing tool={} args={}", call.name(), call.arguments());
                List<HybridSearchResult> results = executeTool(call, question, inquiryId);
                allResults.addAll(results);
            }

            // Deduplicate by chunkId
            List<HybridSearchResult> deduplicated = deduplicateByChunkId(allResults);
            log.info("search-tool-agent: total={} deduplicated={}", allResults.size(), deduplicated.size());

            return rerankingService.rerank(question, deduplicated, FINAL_TOP_K);
        } catch (Exception ex) {
            log.warn("search-tool-agent: LLM tool selection failed -> fallback to hybrid search: {}",
                    ex.getMessage());
            return fallbackSearch(question, inquiryId);
        }
    }

    private List<ToolCall> selectTools(String question, UUID inquiryId) throws Exception {
        String systemPrompt = """
                You are a Bio-Rad technical support search agent.
                Analyze the user's question and select the most appropriate search tools.
                You may call multiple tools if the question requires information from different sources.

                Available context:
                - inquiryId: %s (use this when searching inquiry-specific documents)
                """.formatted(inquiryId != null ? inquiryId.toString() : "none");

        Map<String, Object> body = Map.of(
                "model", chatModel,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", question)
                ),
                "tools", buildToolDefinitions(),
                "tool_choice", "auto",
                "temperature", 0.0
        );

        String response = restClient.post()
                .uri("/chat/completions")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode toolCallsNode = message.path("tool_calls");

        if (toolCallsNode.isMissingNode() || !toolCallsNode.isArray() || toolCallsNode.isEmpty()) {
            // LLM chose not to use tools — use default search
            log.info("search-tool-agent: LLM returned no tool_calls, using default search");
            return List.of(new ToolCall("search_similar_inquiries", Map.of("query", question)));
        }

        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode tc : toolCallsNode) {
            String name = tc.path("function").path("name").asText();
            String argsJson = tc.path("function").path("arguments").asText("{}");
            Map<String, Object> args = objectMapper.readValue(argsJson, new TypeReference<>() {});
            calls.add(new ToolCall(name, args));
        }
        return calls;
    }

    private List<HybridSearchResult> executeTool(ToolCall call, String originalQuestion, UUID inquiryId) {
        return switch (call.name()) {
            case "search_by_product" -> executeSearchByProduct(call, originalQuestion, inquiryId);
            case "search_inquiry_docs" -> executeSearchInquiryDocs(call, originalQuestion, inquiryId);
            case "search_knowledge_base" -> executeSearchKnowledgeBase(call, originalQuestion);
            case "get_document_section" -> executeGetDocumentSection(call, originalQuestion);
            case "search_similar_inquiries" -> executeSearchSimilar(call, originalQuestion);
            default -> {
                log.warn("search-tool-agent: unknown tool={}", call.name());
                yield List.of();
            }
        };
    }

    private List<HybridSearchResult> executeSearchByProduct(ToolCall call, String originalQuestion, UUID inquiryId) {
        String productName = asString(call.arguments().get("product_name"));
        String query = asString(call.arguments().getOrDefault("query", originalQuestion));

        var extracted = productExtractorService.extract(productName);
        if (extracted != null) {
            SearchFilter filter = SearchFilter.forProducts(
                    inquiryId, Set.of(extracted.productFamily()));
            return hybridSearchService.search(query, SEARCH_TOP_K, filter);
        }

        // Fallback: use the product name as-is for search
        return hybridSearchService.search(productName + " " + query, SEARCH_TOP_K,
                inquiryId != null ? SearchFilter.forInquiry(inquiryId) : SearchFilter.none());
    }

    private List<HybridSearchResult> executeSearchInquiryDocs(ToolCall call, String originalQuestion, UUID inquiryId) {
        String query = asString(call.arguments().getOrDefault("query", originalQuestion));
        UUID targetInquiryId = inquiryId;

        Object idArg = call.arguments().get("inquiry_id");
        if (idArg != null) {
            try {
                targetInquiryId = UUID.fromString(asString(idArg));
            } catch (IllegalArgumentException ignored) {
                // use the provided inquiryId
            }
        }

        if (targetInquiryId == null) {
            return hybridSearchService.search(query, SEARCH_TOP_K, SearchFilter.none());
        }

        // Filter to inquiry documents only (exclude KB)
        SearchFilter filter = new SearchFilter(targetInquiryId, null, null, Set.of("INQUIRY"));
        return hybridSearchService.search(query, SEARCH_TOP_K, filter);
    }

    private List<HybridSearchResult> executeSearchKnowledgeBase(ToolCall call, String originalQuestion) {
        String query = asString(call.arguments().getOrDefault("query", originalQuestion));
        // KB-only search
        SearchFilter filter = new SearchFilter(null, null, null, Set.of("KNOWLEDGE_BASE"));
        return hybridSearchService.search(query, SEARCH_TOP_K, filter);
    }

    private List<HybridSearchResult> executeGetDocumentSection(ToolCall call, String originalQuestion) {
        // Mock implementation: search with the query and filter results
        String query = asString(call.arguments().getOrDefault("query", originalQuestion));
        Object docIdArg = call.arguments().get("doc_id");

        if (docIdArg != null) {
            try {
                UUID docId = UUID.fromString(asString(docIdArg));
                SearchFilter filter = SearchFilter.forDocuments(Set.of(docId));
                return hybridSearchService.search(query, SEARCH_TOP_K, filter);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }

        return hybridSearchService.search(query, SEARCH_TOP_K, SearchFilter.none());
    }

    private List<HybridSearchResult> executeSearchSimilar(ToolCall call, String originalQuestion) {
        String query = asString(call.arguments().getOrDefault("query", originalQuestion));
        return hybridSearchService.search(query, SEARCH_TOP_K, SearchFilter.none());
    }

    private List<HybridSearchResult> deduplicateByChunkId(List<HybridSearchResult> results) {
        Map<UUID, HybridSearchResult> seen = new LinkedHashMap<>();
        for (HybridSearchResult r : results) {
            seen.merge(r.chunkId(), r, (existing, newer) ->
                    newer.fusedScore() > existing.fusedScore() ? newer : existing);
        }
        return new ArrayList<>(seen.values());
    }

    private List<RerankingService.RerankResult> fallbackSearch(String question, UUID inquiryId) {
        SearchFilter filter = inquiryId != null
                ? SearchFilter.forInquiry(inquiryId)
                : SearchFilter.none();
        List<HybridSearchResult> results = hybridSearchService.search(question, SEARCH_TOP_K, filter);
        return rerankingService.rerank(question, results, FINAL_TOP_K);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildToolDefinitions() {
        return List.of(
                toolDef("search_by_product",
                        "특정 Bio-Rad 제품 관련 문서를 검색합니다. 제품명이 질문에 언급될 때 사용.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "product_name", Map.of("type", "string",
                                                "description", "Bio-Rad 제품명 (예: QX200, CFX Opus, naica)"),
                                        "query", Map.of("type", "string",
                                                "description", "검색할 질문 또는 키워드")
                                ),
                                "required", List.of("product_name", "query")
                        )),
                toolDef("search_inquiry_docs",
                        "현재 문의에 첨부된 문서만 검색합니다. 첨부 파일 내용을 참조할 때 사용.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "inquiry_id", Map.of("type", "string",
                                                "description", "문의 ID (UUID, 자동 제공)"),
                                        "query", Map.of("type", "string",
                                                "description", "검색할 질문 또는 키워드")
                                ),
                                "required", List.of("query")
                        )),
                toolDef("search_knowledge_base",
                        "사전 등록된 지식 기반(매뉴얼, 프로토콜, FAQ, 스펙시트)을 검색합니다.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string",
                                                "description", "검색할 질문 또는 키워드"),
                                        "category", Map.of("type", "string",
                                                "description", "카테고리 필터 (MANUAL, PROTOCOL, FAQ, SPEC_SHEET)")
                                ),
                                "required", List.of("query")
                        )),
                toolDef("get_document_section",
                        "특정 문서의 내용을 직접 조회합니다. 특정 문서 ID를 알고 있을 때 사용.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "doc_id", Map.of("type", "string",
                                                "description", "문서 ID (UUID)"),
                                        "query", Map.of("type", "string",
                                                "description", "조회할 섹션 관련 키워드")
                                ),
                                "required", List.of("doc_id", "query")
                        )),
                toolDef("search_similar_inquiries",
                        "전체 문서에서 유사한 내용을 광범위하게 검색합니다. 일반적인 질문에 사용.",
                        Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "query", Map.of("type", "string",
                                                "description", "검색할 질문 또는 키워드")
                                ),
                                "required", List.of("query")
                        ))
        );
    }

    private Map<String, Object> toolDef(String name, String description, Map<String, Object> parameters) {
        return Map.of(
                "type", "function",
                "function", Map.of(
                        "name", name,
                        "description", description,
                        "parameters", parameters
                )
        );
    }

    private static String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String truncate(String text, int maxLen) {
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    record ToolCall(String name, Map<String, Object> arguments) {}
}
