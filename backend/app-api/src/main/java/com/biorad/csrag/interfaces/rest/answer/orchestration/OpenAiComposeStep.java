package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.openai.OpenAiRequestUtils;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Component
@Primary
@ConditionalOnProperty(prefix = "openai", name = "enabled", havingValue = "true")
public class OpenAiComposeStep implements ComposeStep {

    private static final Logger log = LoggerFactory.getLogger(OpenAiComposeStep.class);
    /** @deprecated Use token-based budget via {@code rag.compose.evidence-token-budget} instead. Kept for backward compatibility. */
    @Deprecated
    static final int EVIDENCE_CHAR_BUDGET = 12000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String chatModel;
    private final DefaultComposeStep fallback;
    private final PromptRegistry promptRegistry;
    private final int evidenceTokenBudget;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiComposeStep(
            @Value("${openai.api-key}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-heavy:gpt-5-mini}") String chatModel,
            @Value("${rag.compose.evidence-token-budget:3000}") int evidenceTokenBudget,
            ObjectMapper objectMapper,
            DefaultComposeStep fallback,
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
        this.evidenceTokenBudget = evidenceTokenBudget;
    }

    /** Test-visible constructor */
    OpenAiComposeStep(RestClient restClient, String chatModel, int evidenceTokenBudget,
                      ObjectMapper objectMapper, DefaultComposeStep fallback, PromptRegistry promptRegistry) {
        this.restClient = restClient;
        this.chatModel = chatModel;
        this.evidenceTokenBudget = evidenceTokenBudget;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        this.promptRegistry = promptRegistry;
    }

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel) {
        try {
            String systemPrompt = promptRegistry.get("compose-system");
            String prompt = buildPrompt(analysis, tone, channel);

            String content = callLlm(systemPrompt, prompt);

            return new ComposeStepResult(content, fallback.execute(analysis, tone, channel).formatWarnings());
        } catch (Exception ex) {
            log.warn("openai.compose.failed -> fallback to default compose: {}", ex.getMessage());
            return fallback.execute(analysis, tone, channel);
        }
    }

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel,
                                      String additionalInstructions, String previousAnswerDraft) {
        PromptPair prompts = resolvePrompts(analysis, tone, channel, additionalInstructions, previousAnswerDraft);
        if (prompts == null) {
            return execute(analysis, tone, channel);
        }

        try {
            String content = callLlm(prompts.system(), prompts.user());
            List<String> warnings = prompts.isRefinement()
                    ? fallback.execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft).formatWarnings()
                    : fallback.execute(analysis, tone, channel).formatWarnings();
            return new ComposeStepResult(content, warnings);
        } catch (Exception ex) {
            log.warn("openai.compose.{}.failed -> fallback: {}", prompts.mode(), ex.getMessage());
            return prompts.isRefinement()
                    ? fallback.execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft)
                    : fallback.execute(analysis, tone, channel, additionalInstructions, null);
        }
    }

    @Override
    public ComposeStepResult executeStreaming(
            AnalyzeResponse analysis, String tone, String channel,
            String additionalInstructions, String previousAnswerDraft,
            Consumer<String> onToken) {
        try {
            PromptPair prompts = resolvePrompts(analysis, tone, channel, additionalInstructions, previousAnswerDraft);
            String systemPrompt;
            String userPrompt;
            boolean refinement;

            if (prompts != null) {
                systemPrompt = prompts.system();
                userPrompt = prompts.user();
                refinement = prompts.isRefinement();
            } else {
                // Default single-question compose
                systemPrompt = promptRegistry.get("compose-system");
                userPrompt = buildPrompt(analysis, tone, channel);
                refinement = false;
            }

            String draft = callLlmStreaming(systemPrompt, userPrompt, onToken);

            List<String> warnings = refinement
                    ? fallback.execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft).formatWarnings()
                    : fallback.execute(analysis, tone, channel).formatWarnings();
            return new ComposeStepResult(draft, warnings);
        } catch (Exception e) {
            log.warn("Streaming failed, falling back to blocking call: {}", e.getMessage());
            return execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft);
        }
    }

    /**
     * Resolves system/user prompt pair based on compose mode.
     * Returns null if the default single-question compose should be used.
     */
    private PromptPair resolvePrompts(AnalyzeResponse analysis, String tone, String channel,
                                       String additionalInstructions, String previousAnswerDraft) {
        // Refinement mode
        if (previousAnswerDraft != null && !previousAnswerDraft.isBlank()
                && additionalInstructions != null && !additionalInstructions.isBlank()) {
            String normalizedTone = (tone == null || tone.isBlank()) ? "gilseon" : tone.trim().toLowerCase();
            String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();
            return new PromptPair(
                    buildRefinementSystemPrompt(normalizedTone),
                    buildRefinementUserPrompt(previousAnswerDraft, additionalInstructions, analysis, normalizedTone, normalizedChannel),
                    "refine");
        }

        // Per-question compose
        if (additionalInstructions != null && additionalInstructions.contains("[하위 질문별 증거 매핑]")) {
            return new PromptPair(
                    promptRegistry.get("compose-system"),
                    buildPerQuestionPrompt(additionalInstructions, analysis, tone, channel),
                    "perQuestion");
        }

        return null;
    }

    private record PromptPair(String system, String user, String mode) {
        boolean isRefinement() { return "refine".equals(mode); }
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
                        4096,
                        0.3
                ))
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("openai compose empty content");
            }
            return content;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("openai compose response parse failed", e);
        }
    }

    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_BACKOFF_MS = 1000;

    private String callLlmStreaming(String systemPrompt, String userPrompt, Consumer<String> onToken) {
        Object[] messages = new Object[]{
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        };

        Map<String, Object> body = OpenAiRequestUtils.chatBodyStreaming(chatModel, messages, 4096, 0.3);

        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            StringBuilder fullResponse = new StringBuilder();

            try {
                restClient.post()
                        .uri("/chat/completions")
                        .body(body)
                        .exchange((request, response) -> {
                            int statusCode = response.getStatusCode().value();
                            if (statusCode == 429) {
                                throw new RateLimitException("OpenAI rate limit exceeded (429)");
                            }
                            if (statusCode >= 400) {
                                throw new IllegalStateException(
                                        "OpenAI streaming request failed with status " + statusCode);
                            }

                            try (BufferedReader reader = new BufferedReader(
                                    new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (!line.startsWith("data: ")) continue;
                                    String payload = line.substring(6).trim();
                                    if ("[DONE]".equals(payload)) break;

                                    JsonNode node = objectMapper.readTree(payload);
                                    JsonNode delta = node.at("/choices/0/delta/content");
                                    if (delta != null && !delta.isNull() && !delta.isMissingNode()) {
                                        String chunk = delta.asText();
                                        fullResponse.append(chunk);
                                        onToken.accept(chunk);
                                    }

                                    JsonNode usage = node.get("usage");
                                    if (usage != null && !usage.isNull()) {
                                        log.debug("streaming.usage prompt={} completion={} total={}",
                                                usage.path("prompt_tokens").asInt(),
                                                usage.path("completion_tokens").asInt(),
                                                usage.path("total_tokens").asInt());
                                    }
                                }
                            }
                            return fullResponse.toString();
                        });

                String result = fullResponse.toString();
                if (result.isBlank()) {
                    throw new IllegalStateException("openai compose streaming empty content");
                }
                return result;

            } catch (RateLimitException e) {
                if (attempt >= MAX_RETRIES) {
                    throw new IllegalStateException("OpenAI rate limit exceeded after " + (MAX_RETRIES + 1) + " attempts", e);
                }
                long backoffMs = INITIAL_BACKOFF_MS * (1L << attempt);
                log.warn("streaming.rateLimited attempt={}/{} backoff={}ms", attempt + 1, MAX_RETRIES + 1, backoffMs);
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted during rate limit backoff", ie);
                }
            }
        }

        throw new IllegalStateException("openai compose streaming exhausted retries");
    }

    /**
     * Internal exception to signal 429 rate-limit responses during streaming,
     * enabling exponential backoff retry within {@link #callLlmStreaming}.
     */
    private static class RateLimitException extends RuntimeException {
        RateLimitException(String message) { super(message); }
    }

    private String buildRefinementSystemPrompt(String tone) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 Bio-Rad 고객기술지원팀의 응답 작성 전문가이다.\n");
        sb.append("기존 답변을 기반으로 보완 지시사항을 반영하여 답변을 재작성하는 것이 너의 역할이다.\n\n");
        sb.append("반드시 다음 규칙을 지켜라:\n");
        sb.append("1. 기존 답변의 정보와 구조를 최대한 유지하되, 보완 지시사항에 해당하는 부분만 추가/수정하라\n");
        sb.append("2. 기존 답변에 포함된 인용(파일명, p.XX-YY)을 반드시 보존하라\n");
        sb.append("3. 보완 내용을 자연스럽게 기존 답변에 통합하라. 별도 섹션으로 분리하지 마라\n");
        sb.append("4. 중복 내용을 제거하고, 전체 흐름이 자연스럽게 이어지도록 하라\n");
        sb.append("5. 격식체 존댓말 사용 (~드립니다, ~바랍니다, ~겠습니다)\n");
        sb.append("6. 마크다운 서식(##, **, -, 등) 절대 사용 금지. 순수 텍스트만 작성\n");
        sb.append("7. 이모지, 과도한 느낌표 사용 금지\n");
        sb.append("8. 과장/단정 금지, 근거에 없는 내용 추측 금지\n");
        sb.append("9. 번호 인용([1], [2]) 금지. 자연스러운 문맥 인용 사용\n");

        switch (tone) {
            case "gilseon" -> {
                sb.append("\n[길선체 스타일 지시]\n");
                sb.append("다음은 '길선체' 스타일이다. 반드시 이 스타일을 유지하라:\n");
                sb.append("- 인사: \"안녕하세요\" + \"한국바이오래드 차길선 입니다.\"\n");
                sb.append("- 본문: #1), #2), #3) 형식의 번호 매기기\n");
                sb.append("- \"하기와 같이\", \"상기\" 등 표현 사용\n");
                sb.append("- 마무리: \"감사합니다.\" + \"차길선 드림.\"\n");
                sb.append("- 격식체이면서도 친근한 톤, 교육적이고 상세한 기술적 설명\n");
            }
            case "brief" -> {
                sb.append("\n[간결체 스타일 지시]\n");
                sb.append("핵심만 간결하게 전달하라. 불필요한 수식어 배제.\n");
            }
            case "technical" -> {
                sb.append("\n[기술체 스타일 지시]\n");
                sb.append("기술적 정확성을 최우선으로 하라. 파라미터, 조건, 수치 등 구체적 정보 포함.\n");
                sb.append("원리 설명 후 결론 도출. 한영 혼용 기술 용어 사용.\n");
            }
            default -> {
                sb.append("\n[전문가 톤 스타일 지시]\n");
                sb.append("격식체 전문가 톤을 유지하라.\n");
                sb.append("email 채널: 인사/마무리 포함. messenger 채널: [요약] 태그, 260자 이내.\n");
            }
        }

        return sb.toString();
    }

    private String buildRefinementUserPrompt(String previousDraft, String instructions,
                                              AnalyzeResponse analysis, String tone, String channel) {
        StringBuilder sb = new StringBuilder();
        sb.append("[기존 답변]\n");
        sb.append(previousDraft);
        sb.append("\n\n");

        sb.append("[보완 지시사항]\n");
        sb.append(instructions.trim());
        sb.append("\n\n");

        List<EvidenceItem> evidences = analysis.evidences();
        if (evidences != null && !evidences.isEmpty()) {
            List<EvidenceItem> budgeted = applyTokenBudget(evidences);
            sb.append("[참고 자료] (").append(budgeted.size()).append("건)\n");
            for (int i = 0; i < budgeted.size(); i++) {
                sb.append(formatEvidenceCompact(i + 1, budgeted.get(i))).append("\n");
            }
            sb.append("\n");
        }

        sb.append("[채널] ").append(channel).append("\n");
        sb.append("[톤] ").append(tone).append("\n\n");

        sb.append("[지시]\n");
        sb.append("위 보완 지시사항을 반영하여 기존 답변을 재작성하라.\n");
        sb.append("기존 답변의 인용과 핵심 내용은 유지하면서, 보완 사항만 추가/수정하라.\n");
        sb.append("channel=email이면 인사/마무리 포함, messenger면 [요약] 태그로 시작하여 간결하게 작성하라.\n");

        return sb.toString();
    }

    private String buildPrompt(AnalyzeResponse analysis, String tone, String channel) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 분석 결과와 참고 자료를 바탕으로 고객 답변 초안을 한국어 격식체로 작성해줘.\n\n");
        sb.append("[분석 결과]\n");
        sb.append("- tone: ").append(tone == null ? "gilseon" : tone).append("\n");
        sb.append("- channel: ").append(channel == null ? "email" : channel).append("\n\n");

        List<EvidenceItem> evidences = analysis.evidences();
        if (evidences != null && !evidences.isEmpty()) {
            List<EvidenceItem> budgeted = applyTokenBudget(evidences);
            sb.append("[참고 자료] (").append(budgeted.size()).append("건)\n");
            for (int i = 0; i < budgeted.size(); i++) {
                sb.append(formatEvidenceCompact(i + 1, budgeted.get(i))).append("\n");
            }
        }

        sb.append("[지시]\n");
        sb.append("참고 자료의 내용을 기반으로 가능한 한 구체적이고 실용적인 답변을 작성하라.\n");
        sb.append("참고 자료가 영문이더라도 내용이 질문과 관련되면 반드시 활용하여 한국어로 답변하라.\n");
        sb.append("참고 자료에 답변에 필요한 정보가 충분히 있으면 자신 있게 안내하라.\n");
        sb.append("정보가 부족한 부분에 대해서만 추가 확인을 요청하라.\n");
        sb.append("\"단정이 어렵다\", \"특정하기 어렵다\", \"확인되지 않아\" 같은 모호한/회피 표현은 참고 자료에 관련 내용이 전혀 없을 때만 사용하라.\n");
        sb.append("근거가 있는 내용은 명확히 전달하라.\n\n");

        sb.append("[요구사항]\n");
        sb.append("1) 번호 인용([1], [2]) 금지. \"사내 자료를 참고한 결과\" 등 자연스러운 문맥 인용 사용\n");
        sb.append("2) 마크다운 서식(##, **, -, ```) 절대 금지. 순수 텍스트만 작성\n");
        sb.append("3) 이모지, 과도한 느낌표 금지\n");
        sb.append("4) 과장/단정 금지, 근거에 없는 내용 추측 금지\n");
        sb.append("5) channel=email이면 인사(\"안녕하세요.\")/마무리(\"감사합니다.\") 포함, messenger면 [요약] 태그로 시작하여 간결하게\n");
        sb.append("6) 참고 자료의 내용을 인용할 때 해당 자료의 파일명과 페이지 번호를 괄호 안에 자연스럽게 표기할 것. 예: \"~기능이 제공됩니다 (10000107223.pdf, p.94-95)\". 근거가 없는 내용에는 출처 표기 금지\n");
        sb.append("7) 답변 끝에 고객에게 추가 질문이나 후속 확인 항목을 넣지 마라. 안내 내용만 작성하고 마무리하라\n");

        if ("gilseon".equalsIgnoreCase(tone)) {
            sb.append("\n[길선체 스타일 지시]\n");
            sb.append("다음은 '길선체'라 불리는 Bio-Rad 차길선 FAS의 이메일 작성 스타일이다. 반드시 이 스타일을 따라 작성하라:\n");
            sb.append("1) 호칭: \"[고객명]께\" 로 시작. 고객명을 알 수 없으면 \"고객님께\" 사용\n");
            sb.append("2) 인사: \"안녕하세요\" (별도 줄) + \"한국바이오래드 차길선 입니다.\" (별도 줄)\n");
            sb.append("3) 맥락 연결: \"문의 주신 내용 바탕으로 하기와 같이 안내드립니다.\" 또는 \"보내주신 데이터 확인했습니다.\" 등\n");
            sb.append("4) 본문 구조: #1), #2), #3) 형식의 번호 매기기로 포인트별 정리\n");
            sb.append("5) 기술 설명: 원리부터 설명 후 결론 도출. 한영 혼용 기술 용어 자연스럽게 사용\n");
            sb.append("6) 관찰/확인: \"~것을 확인했습니다\", \"~것으로 예상합니다\" 패턴 사용\n");
            sb.append("7) 요청/지시: \"~해주십시오\", \"~부탁드리겠습니다\" 사용\n");
            sb.append("8) 정보 제공: \"~안내드립니다\", \"~남겨드립니다\" 사용\n");
            sb.append("9) 부가 설명: 괄호 안에 실용적 팁 추가 (예: \"(안그러면 lid temp으로 손에 화상 입을 수 있으니까요)\")\n");
            sb.append("10) 추가 안내: \"추가 도움이 될 만한 내용이 생각이 난다면 연락 드리도록 하겠습니다.\" 또는 \"추가 문의 건 언제든 회신 주십시오.\"\n");
            sb.append("11) 마무리: \"감사합니다.\" + \"차길선 드림.\" (각각 별도 줄)\n");
            sb.append("12) 전체 톤: 격식체이면서도 친근한 느낌. 교육적이고 상세한 기술적 설명. \"하기와 같이\", \"상기\" 등 표현 사용\n");
        }

        return sb.toString();
    }

    private String buildPerQuestionPrompt(String subQuestionMapping, AnalyzeResponse analysis,
                                          String tone, String channel) {
        StringBuilder sb = new StringBuilder();
        sb.append("아래 하위 질문별 증거를 바탕으로 고객 답변 초안을 한국어 격식체로 작성해줘.\n");
        sb.append("각 질문에 배정된 증거만 사용하여 답변하라. 증거의 excerpt 내용이 답변의 근거이다.\n\n");
        sb.append(subQuestionMapping).append("\n\n");

        sb.append("[분석 결과]\n");
        sb.append("- tone: ").append(tone == null ? "gilseon" : tone).append("\n");
        sb.append("- channel: ").append(channel == null ? "email" : channel).append("\n\n");

        sb.append("[지시]\n");
        sb.append("각 하위 질문에 대해 해당 질문에 배정된 증거를 활용하여 답변하라.\n");
        sb.append("증거가 충분하지 않은 질문에는 '해당 내용은 현재 등록된 자료에서 확인되지 않아, 확인 후 별도로 답변드리겠습니다.'로 응답하라.\n");
        sb.append("복수 질문이므로 #1), #2), #3) 형식으로 구분하여 답변하라.\n\n");

        sb.append("[요구사항]\n");
        sb.append("1) 번호 인용([1], [2]) 금지. 자연스러운 문맥 인용 사용\n");
        sb.append("2) 마크다운 서식 절대 금지. 순수 텍스트만 작성\n");
        sb.append("3) 이모지, 과도한 느낌표 금지\n");
        sb.append("4) 과장/단정 금지, 근거에 없는 내용 추측 금지\n");
        sb.append("5) 참고 자료 인용 시 파일명과 페이지 번호를 괄호 안에 자연스럽게 표기\n");
        sb.append("6) channel=email이면 인사/마무리 포함, messenger면 간결하게\n");

        if ("gilseon".equalsIgnoreCase(tone)) {
            sb.append("\n[길선체 스타일 지시]\n");
            sb.append("길선체 스타일을 따라 작성하라. #1), #2) 번호 매기기, 인사/마무리 포함.\n");
        }

        return sb.toString();
    }

    // ── Evidence format & budget helpers ──────────────────────────────────

    /**
     * Compact inline evidence format:
     * {@code [index|fileName:pageRange|sourceAbbrev|score] excerpt}
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
        sb.append(excerpt);

        return sb.toString();
    }

    /**
     * Abbreviates source type: KNOWLEDGE_BASE → "KB", INQUIRY → "INQ".
     */
    static String abbreviateSourceType(String sourceType) {
        if (sourceType == null) return "INQ";
        return switch (sourceType) {
            case "KNOWLEDGE_BASE" -> "KB";
            default -> "INQ";
        };
    }

    /**
     * Estimates token count for a string.
     * Uses chars/3 for Korean (multibyte), chars/4 for predominantly ASCII text.
     */
    static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        long nonAsciiCount = text.chars().filter(c -> c > 127).count();
        boolean predominantlyKorean = nonAsciiCount > text.length() / 3;
        return (int) Math.ceil((double) text.length() / (predominantlyKorean ? 3 : 4));
    }

    /**
     * Sorts evidence by score descending, then trims lowest-score items until
     * total estimated tokens fit within {@link #evidenceTokenBudget}.
     * Returns a new list (never mutates the input).
     */
    List<EvidenceItem> applyTokenBudget(List<EvidenceItem> evidences) {
        if (evidences == null || evidences.isEmpty()) return List.of();

        // Sort by score descending (highest relevance first)
        List<EvidenceItem> sorted = new ArrayList<>(evidences);
        sorted.sort(Comparator.comparingDouble(EvidenceItem::score).reversed());

        // Accumulate until budget exceeded
        List<EvidenceItem> result = new ArrayList<>();
        int tokenSum = 0;
        for (int i = 0; i < sorted.size(); i++) {
            String formatted = formatEvidenceCompact(i + 1, sorted.get(i));
            int tokens = estimateTokens(formatted);
            if (!result.isEmpty() && tokenSum + tokens > evidenceTokenBudget) {
                log.info("Evidence budget: trimmed from {} to {} items (budget: {} tokens)",
                        sorted.size(), result.size(), evidenceTokenBudget);
                break;
            }
            result.add(sorted.get(i));
            tokenSum += tokens;
        }

        return result;
    }

    /** Exposes token budget for testing. */
    int getEvidenceTokenBudget() {
        return evidenceTokenBudget;
    }

}
