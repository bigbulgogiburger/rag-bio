package com.biorad.csrag.application.knowledge;

import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;
import java.util.Map;

/**
 * 문서 텍스트를 분석하여 메타데이터(카테고리, 제품군, 설명, 태그)를 AI로 추천한다.
 * openai.enabled=false이면 분석을 건너뛴다.
 */
@Component
public class DocumentMetadataAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(DocumentMetadataAnalyzer.class);
    private static final int MAX_ANALYZE_CHARS = 5000;

    private final DocumentTextExtractor textExtractor;
    private final ObjectMapper objectMapper;
    private final boolean openaiEnabled;
    private final RestClient restClient;
    private final String chatModel;
    private final PromptRegistry promptRegistry;

    public DocumentMetadataAnalyzer(
            DocumentTextExtractor textExtractor,
            ObjectMapper objectMapper,
            @Value("${openai.enabled:false}") boolean openaiEnabled,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-light:gpt-4.1-mini}") String chatModel,
            PromptRegistry promptRegistry
    ) {
        this.textExtractor = textExtractor;
        this.objectMapper = objectMapper;
        this.openaiEnabled = openaiEnabled;
        this.chatModel = chatModel;
        this.promptRegistry = promptRegistry;

        if (openaiEnabled && apiKey != null && !apiKey.isBlank()) {
            this.restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    public boolean isAvailable() {
        return openaiEnabled && restClient != null;
    }

    /**
     * 파일에서 텍스트를 추출하고 OpenAI로 메타데이터를 분석한다.
     *
     * @return 분석 결과. 실패 시 null 반환 (호출측에서 무시 가능).
     */
    public MetadataSuggestion analyze(Path filePath, String contentType) {
        if (!isAvailable()) {
            log.debug("metadata.analyzer.skipped reason=openai_disabled");
            return null;
        }

        try {
            String text = textExtractor.extract(filePath, contentType);
            if (text == null || text.isBlank()) {
                log.warn("metadata.analyzer.skipped reason=empty_text path={}", filePath.getFileName());
                return null;
            }

            String truncated = text.length() > MAX_ANALYZE_CHARS
                    ? text.substring(0, MAX_ANALYZE_CHARS)
                    : text;

            return callOpenAi(truncated);
        } catch (Exception e) {
            log.warn("metadata.analyzer.failed path={} error={}", filePath.getFileName(), e.getMessage());
            return null;
        }
    }

    private MetadataSuggestion callOpenAi(String documentText) throws Exception {
        String systemPrompt = promptRegistry != null
                ? promptRegistry.get("metadata-analysis")
                : """
                당신은 Bio-Rad 기술 문서 분류 전문가입니다.
                업로드된 문서의 텍스트를 분석하여 메타데이터를 추출하세요.

                카테고리 분류 기준:
                - MANUAL: 제품 사용자 매뉴얼, 설치 가이드
                - PROTOCOL: 실험 프로토콜, SOP
                - FAQ: 자주 묻는 질문, Q&A 문서
                - SPEC_SHEET: 제품 사양서, 데이터시트
                - TROUBLESHOOTING: 문제 해결 가이드
                - OTHER: 위에 해당하지 않는 문서

                반드시 아래 JSON 형식으로만 응답하세요:
                {"category":"MANUAL","productFamily":"제품군명","description":"2-3문장 요약","tags":"키워드1, 키워드2, 키워드3"}
                """;

        String response = restClient.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", chatModel,
                        "messages", new Object[]{
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", "문서 텍스트:\n\n" + documentText)
                        },
                        "temperature", 0.1
                ))
                .retrieve()
                .body(String.class);

        JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
        String content = root.path("choices").path(0).path("message").path("content").asText("").trim();

        if (content.isBlank()) {
            log.warn("metadata.analyzer.empty_response");
            return null;
        }

        // JSON 블록 추출 (```json ... ``` 감싸져 있을 수 있음)
        String json = content;
        if (json.contains("{")) {
            json = json.substring(json.indexOf("{"), json.lastIndexOf("}") + 1);
        }

        JsonNode suggestion = objectMapper.readTree(json);
        return new MetadataSuggestion(
                suggestion.path("category").asText(null),
                suggestion.path("productFamily").asText(null),
                suggestion.path("description").asText(null),
                suggestion.path("tags").asText(null)
        );
    }

    public record MetadataSuggestion(
            String category,
            String productFamily,
            String description,
            String tags
    ) {
    }
}
