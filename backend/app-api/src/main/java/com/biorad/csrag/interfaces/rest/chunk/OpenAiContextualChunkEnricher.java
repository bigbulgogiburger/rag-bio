package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiContextualChunkEnricher implements ContextualChunkEnricher {

    private static final Logger log = LoggerFactory.getLogger(OpenAiContextualChunkEnricher.class);

    private static final String CONTEXT_PROMPT = """
            <document>
            %s
            </document>

            다음은 위 문서에서 추출한 하나의 청크입니다:
            <chunk>
            %s
            </chunk>

            이 청크의 문맥을 1-2문장으로 요약하세요.
            문서 파일명(%s), 섹션명, 제품명, 이 청크가 설명하는 내용의 맥락을 포함하세요.
            한국어로 작성하세요.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final MockContextualChunkEnricher fallback;
    private final PromptRegistry promptRegistry;

    public OpenAiContextualChunkEnricher(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-light:gpt-4.1-mini}") String chatModel,
            ObjectMapper objectMapper,
            MockContextualChunkEnricher fallback,
            PromptRegistry promptRegistry
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.fallback = fallback;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public void enrichChunks(String documentText, List<DocumentChunkJpaEntity> chunks, String fileName) {
        if (chunks == null || chunks.isEmpty()) return;

        // 1. Parent 청크만 필터 (context는 parent에서 생성, child에 상속)
        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel()))
                .toList();

        // flat 구조 (parent-child 없음)이면 전체 청크 대상
        if (parents.isEmpty()) {
            parents = chunks;
        }

        // 2. 토큰 한도 방지를 위해 문서 텍스트 잘라내기 (~6000자)
        String truncatedDoc = documentText != null && documentText.length() > 6000
                ? documentText.substring(0, 6000) + "..."
                : (documentText != null ? documentText : "");

        // 3. Parent별 context prefix 생성
        Map<UUID, String> contextMap = new HashMap<>();
        for (DocumentChunkJpaEntity parent : parents) {
            String contextPrefix = generateContextPrefix(truncatedDoc, parent.getContent(), fileName);
            contextMap.put(parent.getId(), contextPrefix);
            parent.setContextPrefix(contextPrefix);
            String enriched = contextPrefix.isEmpty()
                    ? parent.getContent()
                    : contextPrefix + "\n" + parent.getContent();
            parent.setEnrichedContent(enriched);
        }

        // 4. Child 청크는 parent context 상속
        for (DocumentChunkJpaEntity chunk : chunks) {
            if ("CHILD".equals(chunk.getChunkLevel()) && chunk.getParentChunkId() != null) {
                String parentContext = contextMap.get(chunk.getParentChunkId());
                if (parentContext != null && !parentContext.isEmpty()) {
                    chunk.setContextPrefix(parentContext);
                    chunk.setEnrichedContent(parentContext + "\n" + chunk.getContent());
                } else {
                    chunk.setEnrichedContent(chunk.getContent());
                }
            } else if (!"PARENT".equals(chunk.getChunkLevel())) {
                // flat 청크 — 위에서 이미 enriched 된 경우 스킵
                if (!contextMap.containsKey(chunk.getId())) {
                    chunk.setEnrichedContent(chunk.getContent());
                }
            }
        }
    }

    private String generateContextPrefix(String documentText, String chunkContent, String fileName) {
        try {
            String prompt = promptRegistry != null
                    ? promptRegistry.get("contextual-enrichment", Map.of("documentText", documentText, "chunkContent", chunkContent, "fileName", fileName != null ? fileName : ""))
                    : String.format(CONTEXT_PROMPT, documentText, chunkContent, fileName);

            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> body = Map.of(
                    "model", chatModel,
                    "messages", List.of(message),
                    "temperature", 0.3,
                    "max_tokens", 200
            );

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isMissingNode() || content.isNull()) {
                log.warn("contextual.enrichment: empty response from OpenAI");
                return "";
            }
            return content.asText().trim();
        } catch (Exception ex) {
            log.warn("contextual.enrichment.failed for chunk, using empty prefix: {}", ex.getMessage());
            return "";
        }
    }
}
