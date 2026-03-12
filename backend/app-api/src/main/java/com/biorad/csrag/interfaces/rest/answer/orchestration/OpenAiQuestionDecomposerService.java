package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.openai.OpenAiRequestUtils;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.interfaces.rest.search.ProductExtractorService;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM 기반 질문 분해 서비스.
 * <p>
 * Light 모델을 사용하여 고객 질문을 의미 단위로 분해한다.
 * 정규식으로 분리할 수 없는 암묵적 복합 질문도 처리 가능.
 * (예: "CFX96에서 melt curve 피크가 2개 나오는 이유랑 해결법 알려주세요")
 * <p>
 * LLM 호출 실패 시 정규식 기반 폴백으로 자동 전환된다.
 */
@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiQuestionDecomposerService implements QuestionDecomposerService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiQuestionDecomposerService.class);
    private static final int MAX_SUB_QUESTIONS = 5;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final PromptRegistry promptRegistry;
    private final ProductExtractorService productExtractorService;
    private final RegexQuestionDecomposerService regexFallback;

    public OpenAiQuestionDecomposerService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-light:gpt-5-nano}") String chatModel,
            ObjectMapper objectMapper,
            PromptRegistry promptRegistry,
            ProductExtractorService productExtractorService,
            RegexQuestionDecomposerService regexFallback) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.promptRegistry = promptRegistry;
        this.productExtractorService = productExtractorService;
        this.regexFallback = regexFallback;
    }

    // visible-for-testing
    OpenAiQuestionDecomposerService(RestClient restClient, ObjectMapper objectMapper,
                                     String chatModel, PromptRegistry promptRegistry,
                                     ProductExtractorService productExtractorService,
                                     RegexQuestionDecomposerService regexFallback) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.promptRegistry = promptRegistry;
        this.productExtractorService = productExtractorService;
        this.regexFallback = regexFallback;
    }

    @Override
    public DecomposedQuestion decompose(String question) {
        if (question == null || question.isBlank()) {
            log.debug("Empty question received, returning single empty sub-question");
            return new DecomposedQuestion(question, List.of(new SubQuestion(1, "", null)), null);
        }

        try {
            return decomposeWithLlm(question);
        } catch (Exception ex) {
            log.warn("openai.question-decompose.failed -> falling back to regex: {}", ex.getMessage());
            return regexFallback.decompose(question);
        }
    }

    private DecomposedQuestion decomposeWithLlm(String question) {
        String userPrompt = promptRegistry.get("question-decompose", Map.of("question", question));

        String response = restClient.post()
                .uri("/chat/completions")
                .body(OpenAiRequestUtils.chatBodyWithJsonMode(
                        chatModel,
                        List.of(
                                Map.of("role", "system", "content",
                                        "You are a Bio-Rad technical support question analyzer. "
                                        + "Always respond with valid JSON."),
                                Map.of("role", "user", "content", userPrompt)
                        ),
                        4096
                ))
                .retrieve()
                .body(String.class);

        JsonNode root;
        try {
            root = objectMapper.readTree(response == null ? "{}" : response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse question decompose LLM response", e);
        }

        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("Empty response from LLM for question decomposition");
        }

        List<SubQuestion> subQuestions = parseSubQuestions(content, question);
        if (subQuestions.isEmpty()) {
            throw new IllegalStateException("LLM returned no sub-questions");
        }

        // 제품 컨텍스트 추출 (ProductExtractorService 활용)
        ProductExtractorService.ExtractedProduct mainProduct = productExtractorService.extract(question);
        String productContext = mainProduct != null ? mainProduct.productName() : null;

        // 하위 질문별 제품 패밀리 enrichment
        subQuestions = enrichWithProductFamilies(subQuestions);

        log.info("openai.question-decompose: {} sub-questions from '{}'",
                subQuestions.size(), truncate(question, 80));

        return new DecomposedQuestion(question, subQuestions, productContext);
    }

    private List<SubQuestion> parseSubQuestions(String jsonContent, String originalQuestion) {
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(jsonContent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("LLM returned invalid JSON: " + e.getMessage(), e);
        }

        JsonNode subQuestionsNode = parsed.path("subQuestions");
        if (!subQuestionsNode.isArray() || subQuestionsNode.isEmpty()) {
            throw new IllegalStateException("LLM response missing or empty 'subQuestions' array");
        }

        List<SubQuestion> result = new ArrayList<>();
        int index = 1;

        for (JsonNode item : subQuestionsNode) {
            if (index > MAX_SUB_QUESTIONS) {
                log.warn("openai.question-decompose: truncating at {} sub-questions", MAX_SUB_QUESTIONS);
                break;
            }

            String query = item.path("query").asText("").strip();
            if (query.isBlank()) {
                continue;
            }

            // type 필드는 로깅/메트릭용으로만 사용
            String type = item.path("type").asText("info");
            log.debug("openai.question-decompose: sub[{}] type={} query='{}'", index, type, truncate(query, 60));

            // productContext는 원본 질문에서 추출한 것을 공유
            ProductExtractorService.ExtractedProduct product = productExtractorService.extract(query);
            String context = product != null ? product.productName() : null;

            result.add(new SubQuestion(index, query, context));
            index++;
        }

        return result;
    }

    /**
     * 각 하위 질문에서 제품명을 추출하여 productFamilies를 설정한다.
     */
    private List<SubQuestion> enrichWithProductFamilies(List<SubQuestion> subQuestions) {
        List<SubQuestion> enriched = new ArrayList<>(subQuestions.size());
        for (SubQuestion sq : subQuestions) {
            List<ProductExtractorService.ExtractedProduct> products =
                    productExtractorService.extractAll(sq.question());
            if (products.isEmpty()) {
                enriched.add(sq);
            } else {
                Set<String> families = products.stream()
                        .map(ProductExtractorService.ExtractedProduct::productFamily)
                        .collect(Collectors.toSet());
                enriched.add(new SubQuestion(sq.index(), sq.question(), sq.context(), families));
            }
        }
        return enriched;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
