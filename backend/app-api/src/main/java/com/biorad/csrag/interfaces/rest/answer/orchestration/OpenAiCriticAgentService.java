package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.application.ops.RagMetricsService;
import com.biorad.csrag.infrastructure.openai.OpenAiRequestUtils;
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
    /** Threshold above which subsequent Critic calls can be skipped (already high quality). */
    private static final double SKIP_THRESHOLD = 0.90;
    /** Maximum excerpt length per evidence item (chars). */
    private static final int MAX_EXCERPT_LENGTH = 300;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final RagMetricsService ragMetricsService;
    private final PromptRegistry promptRegistry;

    /**
     * Tracks the last faithfulness score from a Critic call.
     * When >= {@link #SKIP_THRESHOLD}, subsequent calls in the same recompose loop can be skipped.
     * Thread-safety note: this service is typically scoped per-request or called sequentially.
     */
    private Double lastFaithfulnessScore;

    @Autowired
    public OpenAiCriticAgentService(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-heavy:gpt-5-mini}") String chatModel,
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
        // Skip subsequent Critic calls if previous score was already high quality
        if (lastFaithfulnessScore != null && lastFaithfulnessScore >= SKIP_THRESHOLD) {
            log.info("critic.skipped: previous faithfulness_score={} >= {} threshold",
                    String.format("%.2f", lastFaithfulnessScore), SKIP_THRESHOLD);
            return CriticResult.passing(lastFaithfulnessScore);
        }

        try {
            String formattedEvidences = formatEvidences(evidences);
            String userPrompt = buildCriticPrompt(draft, question, formattedEvidences);
            String systemPrompt = promptRegistry.get("critic-system");
            String response = callLlm(systemPrompt, userPrompt);
            CriticResult result = parseCriticResponse(response);
            lastFaithfulnessScore = result.faithfulnessScore();
            if (ragMetricsService != null) ragMetricsService.record(null, "CRITIC_REVISION", result.needsRevision() ? 1.0 : 0.0);
            return result;
        } catch (Exception e) {
            log.warn("critic.agent.failed, returning default passing result: {}", e.getMessage());
            return CriticResult.passing(1.0);
        }
    }

    /** Resets the skip-tracking state. Call this when starting a new orchestration cycle. */
    public void resetSkipState() {
        this.lastFaithfulnessScore = null;
    }

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
            sb.append(formatEvidenceCompact(i + 1, ev)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Compact inline evidence format with excerpt truncation:
     * {@code [index|fileName:pageRange|sourceAbbrev|score] excerpt (max 300 chars)}
     */
    static String formatEvidenceCompact(int index, EvidenceItem ev) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(index).append("|");

        String fileName = ev.fileName() != null ? ev.fileName() : "unknown";
        sb.append(fileName);
        if (ev.pageStart() != null) {
            sb.append(":").append(ev.pageStart());
            if (ev.pageEnd() != null && !ev.pageEnd().equals(ev.pageStart())) {
                sb.append("-").append(ev.pageEnd());
            }
        }
        sb.append("|");

        sb.append(abbreviateSourceType(ev.sourceType()));
        sb.append("|");
        sb.append(String.format("%.2f", ev.score()));
        sb.append("] ");

        String excerpt = ev.excerpt() != null ? ev.excerpt() : "";
        if (excerpt.length() > MAX_EXCERPT_LENGTH) {
            excerpt = excerpt.substring(0, MAX_EXCERPT_LENGTH) + "...";
        }
        sb.append(excerpt);

        return sb.toString();
    }

    static String abbreviateSourceType(String sourceType) {
        if (sourceType == null) return "INQ";
        return switch (sourceType) {
            case "KNOWLEDGE_BASE" -> "KB";
            default -> "INQ";
        };
    }

    private String callLlm(String systemPrompt, String userPrompt) {
        String response = restClient.post()
                .uri("/chat/completions")
                .body(OpenAiRequestUtils.chatBody(
                        chatModel,
                        new Object[]{
                                Map.of("role", "system", "content", systemPrompt),
                                Map.of("role", "user", "content", userPrompt)
                        },
                        4096
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
