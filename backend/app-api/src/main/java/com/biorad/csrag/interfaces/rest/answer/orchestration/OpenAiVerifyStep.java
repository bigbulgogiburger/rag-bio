package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiVerifyStep implements VerifyStep {

    private static final Logger log = LoggerFactory.getLogger(OpenAiVerifyStep.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final DefaultVerifyStep fallback;
    private final PromptRegistry promptRegistry;

    public OpenAiVerifyStep(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-medium:gpt-4.1}") String chatModel,
            ObjectMapper objectMapper,
            DefaultVerifyStep fallback,
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
    public AnalyzeResponse execute(UUID inquiryId, String question, List<EvidenceItem> evidences) {
        if (evidences.isEmpty()) {
            return fallback.execute(inquiryId, question, evidences);
        }

        try {
            String prompt = buildPrompt(question, evidences);

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", chatModel,
                            "messages", new Object[]{
                                    Map.of("role", "system", "content", promptRegistry != null ? promptRegistry.get("verify-system") : SYSTEM_PROMPT),
                                    Map.of("role", "user", "content", prompt)
                            },
                            "temperature", 0.1
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("openai verify empty content");
            }

            return parseVerifyResponse(inquiryId, content, evidences);
        } catch (Exception ex) {
            log.warn("openai.verify.failed -> fallback to rule-based: {}", ex.getMessage());
            return fallback.execute(inquiryId, question, evidences);
        }
    }

    private AnalyzeResponse parseVerifyResponse(UUID inquiryId, String content, List<EvidenceItem> evidences) {
        try {
            JsonNode json = objectMapper.readTree(content);
            String verdict = json.path("verdict").asText("CONDITIONAL").toUpperCase();
            double confidence = json.path("confidence").asDouble(0.5);
            String reason = json.path("reason").asText("LLM 검증 결과입니다.");

            List<String> riskFlags = new ArrayList<>();
            JsonNode flagsNode = json.path("riskFlags");
            if (flagsNode.isArray()) {
                for (JsonNode flag : flagsNode) {
                    riskFlags.add(flag.asText());
                }
            }

            if (!List.of("SUPPORTED", "REFUTED", "CONDITIONAL").contains(verdict)) {
                verdict = "CONDITIONAL";
            }
            confidence = Math.max(0.0, Math.min(1.0, confidence));

            return new AnalyzeResponse(inquiryId.toString(), verdict, confidence, reason, riskFlags, evidences, null);
        } catch (Exception ex) {
            log.warn("openai.verify.parse.failed, extracting from text: {}", ex.getMessage());
            return parseFromPlainText(inquiryId, content, evidences);
        }
    }

    private AnalyzeResponse parseFromPlainText(UUID inquiryId, String content, List<EvidenceItem> evidences) {
        String upper = content.toUpperCase();
        String verdict;
        if (upper.contains("SUPPORTED")) {
            verdict = "SUPPORTED";
        } else if (upper.contains("REFUTED")) {
            verdict = "REFUTED";
        } else {
            verdict = "CONDITIONAL";
        }

        double avgScore = evidences.stream().mapToDouble(EvidenceItem::score).average().orElse(0.5);

        return new AnalyzeResponse(
                inquiryId.toString(),
                verdict,
                Math.round(avgScore * 1000d) / 1000d,
                content.length() > 300 ? content.substring(0, 300) : content,
                List.of(),
                evidences,
                null
        );
    }

    private String buildPrompt(String question, List<EvidenceItem> evidences) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 질문\n").append(question).append("\n\n");
        sb.append("## 검색된 근거 (").append(evidences.size()).append("건)\n");

        for (int i = 0; i < evidences.size(); i++) {
            EvidenceItem ev = evidences.get(i);
            sb.append("[").append(i + 1).append("] (유사도: ")
                    .append(String.format("%.3f", ev.score()))
                    .append(", 출처: ").append(ev.sourceType()).append(")\n")
                    .append(ev.excerpt()).append("\n\n");
        }

        sb.append("## 지시사항\n");
        sb.append("위 근거를 바탕으로 질문의 주장이 타당한지 검증하고, 아래 JSON 형식으로만 응답하라:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"verdict\": \"SUPPORTED | REFUTED | CONDITIONAL\",\n");
        sb.append("  \"confidence\": 0.0~1.0,\n");
        sb.append("  \"reason\": \"판정 이유 (한국어, 2~3문장)\",\n");
        sb.append("  \"riskFlags\": [\"CONFLICTING_EVIDENCE\", \"LOW_CONFIDENCE\", \"WEAK_EVIDENCE_MATCH\"]\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    private static final String SYSTEM_PROMPT =
            "너는 Bio-Rad 고객 기술 문의에 대한 근거 검증 전문가다. "
                    + "질문과 검색된 근거 문서를 비교 분석하여 질문의 주장이 문서에 의해 뒷받침되는지 판정하라. "
                    + "반드시 JSON 형식으로만 응답하라. "
                    + "verdict는 SUPPORTED(근거가 질문을 뒷받침), REFUTED(근거가 질문에 반함), CONDITIONAL(판단 보류/추가 확인 필요) 중 하나다. "
                    + "과장하지 말고 근거에 없는 내용을 추측하지 마라.";
}
