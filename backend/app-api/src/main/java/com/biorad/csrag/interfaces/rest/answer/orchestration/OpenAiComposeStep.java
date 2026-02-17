package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiComposeStep implements ComposeStep {

    private static final Logger log = LoggerFactory.getLogger(OpenAiComposeStep.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final DefaultComposeStep fallback;

    public OpenAiComposeStep(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper,
            DefaultComposeStep fallback
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.chatModel = chatModel;
        this.fallback = fallback;
    }

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel) {
        try {
            String prompt = buildPrompt(analysis, tone, channel);

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", chatModel,
                            "messages", new Object[]{
                                    Map.of("role", "system", "content", "너는 Bio-Rad 고객 서비스팀의 한국어 비즈니스 이메일 작성 전문가이다.\n반드시 다음 규칙을 지켜라:\n1. 격식체 존댓말 사용 (~드립니다, ~바랍니다, ~겠습니다)\n2. email 채널: 인사(\"안녕하세요.\") → 맥락 → 본론 → 마무리(\"감사합니다.\")\n3. messenger 채널: [요약] 태그로 시작, 260자 이내, 간결하게\n4. 한 문장에 하나의 의미만 담아 짧고 명확하게 작성\n5. 마크다운 서식(##, **, -, 등) 절대 사용 금지. 순수 텍스트만 작성\n6. [1], [2] 같은 번호 인용 금지. \"사내 자료를 참고한 결과\" 등 자연스러운 표현 사용\n7. 이모지, 과도한 느낌표 사용 금지\n8. 과장/단정 금지, 근거에 없는 내용 추측 금지"),
                                    Map.of("role", "user", "content", prompt)
                            },
                            "temperature", 0.2
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("openai compose empty content");
            }

            return new ComposeStepResult(content, fallback.execute(analysis, tone, channel).formatWarnings());
        } catch (Exception ex) {
            log.warn("openai.compose.failed -> fallback to default compose: {}", ex.getMessage());
            return fallback.execute(analysis, tone, channel);
        }
    }

    private String buildPrompt(AnalyzeResponse analysis, String tone, String channel) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 분석 결과와 참고 자료를 바탕으로 고객 답변 초안을 한국어 격식체로 작성해줘.\n\n");
        sb.append("[분석 결과]\n");
        sb.append("- tone: ").append(tone == null ? "professional" : tone).append("\n");
        sb.append("- channel: ").append(channel == null ? "email" : channel).append("\n");
        sb.append("- verdict: ").append(analysis.verdict()).append("\n");
        sb.append("- confidence: ").append(analysis.confidence()).append("\n");
        sb.append("- riskFlags: ").append(analysis.riskFlags()).append("\n");
        sb.append("- reason: ").append(analysis.reason()).append("\n\n");

        List<com.biorad.csrag.interfaces.rest.analysis.EvidenceItem> evidences = analysis.evidences();
        if (evidences != null && !evidences.isEmpty()) {
            sb.append("[참고 자료] (").append(evidences.size()).append("건)\n");
            int limit = Math.min(5, evidences.size());
            for (int i = 0; i < limit; i++) {
                var ev = evidences.get(i);
                sb.append("- (유사도: ")
                        .append(String.format("%.3f", ev.score()))
                        .append(", 출처: ").append(ev.sourceType()).append(")\n")
                        .append(ev.excerpt()).append("\n\n");
            }
        }

        sb.append("[요구사항]\n");
        sb.append("1) 번호 인용([1], [2]) 금지. \"사내 자료를 참고한 결과\" 등 자연스러운 문맥 인용 사용\n");
        sb.append("2) 마크다운 서식(##, **, -, ```) 절대 금지. 순수 텍스트만 작성\n");
        sb.append("3) 이모지, 과도한 느낌표 금지\n");
        sb.append("4) 과장/단정 금지, 근거에 없는 내용 추측 금지\n");
        sb.append("5) 후속 확인 항목 1~3개 포함\n");
        sb.append("6) channel=email이면 인사(\"안녕하세요.\")/마무리(\"감사합니다.\") 포함, messenger면 [요약] 태그로 시작하여 간결하게\n");

        return sb.toString();
    }
}
