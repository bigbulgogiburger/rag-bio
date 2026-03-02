package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.application.ops.RagMetricsService;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
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
import java.util.List;
import java.util.Map;

@Service
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiCriticAgentService implements CriticAgentService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCriticAgentService.class);
    private static final double REVISION_THRESHOLD = 0.70;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final RagMetricsService ragMetricsService;
    private final PromptRegistry promptRegistry;

    @Autowired
    public OpenAiCriticAgentService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-heavy:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper,
            RagMetricsService ragMetricsService,
            PromptRegistry promptRegistry
    ) {
        this(RestClient.builder()
                        .baseUrl(baseUrl)
                        .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .build(),
                chatModel, objectMapper, ragMetricsService, promptRegistry);
    }

    /** 테스트용 생성자 — RestClient를 직접 주입 */
    OpenAiCriticAgentService(RestClient restClient, String chatModel, ObjectMapper objectMapper,
                              RagMetricsService ragMetricsService) {
        this.restClient = restClient;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.ragMetricsService = ragMetricsService;
        this.promptRegistry = null;
    }

    OpenAiCriticAgentService(RestClient restClient, String chatModel, ObjectMapper objectMapper,
                              RagMetricsService ragMetricsService, PromptRegistry promptRegistry) {
        this.restClient = restClient;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.ragMetricsService = ragMetricsService;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public CriticResult critique(String draft, String question, List<EvidenceItem> evidences) {
        try {
            String formattedEvidences = formatEvidences(evidences);
            String userPrompt = buildCriticPrompt(draft, question, formattedEvidences);
            String systemPrompt = promptRegistry != null ? promptRegistry.get("critic-system") : CRITIC_SYSTEM_PROMPT;
            String response = callLlm(systemPrompt, userPrompt);
            CriticResult result = parseCriticResponse(response);
            if (ragMetricsService != null) ragMetricsService.record(null, "CRITIC_REVISION", result.needsRevision() ? 1.0 : 0.0);
            return result;
        } catch (Exception e) {
            log.warn("critic.agent.failed, returning default passing result: {}", e.getMessage());
            return CriticResult.passing(1.0);
        }
    }

    private static final String CRITIC_SYSTEM_PROMPT = """
            당신은 Bio-Rad 기술 문서 사실 검증 전문가입니다.
            답변의 각 주장이 제공된 근거 자료에 기반하는지 엄밀히 검증합니다.
            항상 JSON 형식으로 응답하세요.
            """;

    private String buildCriticPrompt(String draft, String question, String formattedEvidences) {
        return String.format("""
                ## 원본 질문
                %s

                ## 생성된 답변 초안
                %s

                ## 참조 근거 자료
                %s

                ## 검증 요청
                다음 JSON 형식으로 답변의 신뢰도를 평가하세요:
                {
                  "faithfulness_score": 0.0-1.0,
                  "claims": [
                    {
                      "claim": "검증할 주장",
                      "faithfulness": "TRUE|FALSE",
                      "citation_accuracy": "CORRECT|INCORRECT|MISSING",
                      "factual_match": "MATCH|MISMATCH|NOT_APPLICABLE"
                    }
                  ],
                  "corrections": ["수정 제안 1", "수정 제안 2"],
                  "needs_revision": true|false
                }
                """, question, draft, formattedEvidences);
    }

    private String formatEvidences(List<EvidenceItem> evidences) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < evidences.size(); i++) {
            EvidenceItem ev = evidences.get(i);
            sb.append("[근거 ").append(i + 1).append("]");
            if (ev.fileName() != null) {
                sb.append(" 파일: ").append(ev.fileName());
                if (ev.pageStart() != null) {
                    sb.append(", p.").append(ev.pageStart());
                }
            }
            sb.append("\n").append(ev.excerpt()).append("\n\n");
        }
        return sb.toString();
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        String response = restClient.post()
                .uri("/chat/completions")
                .body(Map.of(
                        "model", chatModel,
                        "messages", new Object[]{
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)
                        },
                        "temperature", 0.1
                ))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("openai critic empty content");
            }
            return content;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("openai critic response parse failed", e);
        }
    }

    private CriticResult parseCriticResponse(String response) {
        try {
            String json = response;
            int jsonStart = response.indexOf('{');
            int jsonEnd = response.lastIndexOf('}');
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                json = response.substring(jsonStart, jsonEnd + 1);
            }

            JsonNode root = objectMapper.readTree(json);

            double faithfulnessScore = root.path("faithfulness_score").asDouble(1.0);

            List<ClaimVerification> claims = new ArrayList<>();
            JsonNode claimsNode = root.path("claims");
            if (claimsNode.isArray()) {
                for (JsonNode claimNode : claimsNode) {
                    claims.add(new ClaimVerification(
                            claimNode.path("claim").asText(""),
                            claimNode.path("faithfulness").asText("TRUE"),
                            claimNode.path("citation_accuracy").asText("CORRECT"),
                            claimNode.path("factual_match").asText("MATCH")
                    ));
                }
            }

            List<String> corrections = new ArrayList<>();
            JsonNode correctionsNode = root.path("corrections");
            if (correctionsNode.isArray()) {
                for (JsonNode c : correctionsNode) {
                    corrections.add(c.asText());
                }
            }

            boolean needsRevision = root.path("needs_revision").asBoolean(false);
            if (faithfulnessScore < REVISION_THRESHOLD) {
                needsRevision = true;
            }

            if (needsRevision) {
                return CriticResult.failing(faithfulnessScore, claims, corrections);
            }
            return new CriticResult(faithfulnessScore, claims, corrections, false);
        } catch (Exception e) {
            log.warn("critic.parse.failed, returning default passing result: {}", e.getMessage());
            return CriticResult.passing(1.0);
        }
    }
}
