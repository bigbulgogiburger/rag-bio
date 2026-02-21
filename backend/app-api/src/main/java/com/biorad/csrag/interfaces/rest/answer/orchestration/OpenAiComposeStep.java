package com.biorad.csrag.interfaces.rest.answer.orchestration;

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

            String content = callLlm(SYSTEM_PROMPT, prompt);

            return new ComposeStepResult(content, fallback.execute(analysis, tone, channel).formatWarnings());
        } catch (Exception ex) {
            log.warn("openai.compose.failed -> fallback to default compose: {}", ex.getMessage());
            return fallback.execute(analysis, tone, channel);
        }
    }

    @Override
    public ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel,
                                      String additionalInstructions, String previousAnswerDraft) {
        if (previousAnswerDraft == null || previousAnswerDraft.isBlank()
                || additionalInstructions == null || additionalInstructions.isBlank()) {
            return execute(analysis, tone, channel);
        }

        try {
            String normalizedTone = (tone == null || tone.isBlank()) ? "gilseon" : tone.trim().toLowerCase();
            String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

            String systemPrompt = buildRefinementSystemPrompt(normalizedTone);
            String userPrompt = buildRefinementUserPrompt(previousAnswerDraft, additionalInstructions, analysis, normalizedTone, normalizedChannel);

            String refined = callLlm(systemPrompt, userPrompt);

            List<String> warnings = fallback.execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft).formatWarnings();
            return new ComposeStepResult(refined, warnings);
        } catch (Exception ex) {
            log.warn("openai.compose.refine.failed -> fallback to default compose: {}", ex.getMessage());
            return fallback.execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft);
        }
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
                        "temperature", 0.3
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
            sb.append("[참고 자료] (").append(evidences.size()).append("건)\n");
            int limit = Math.min(5, evidences.size());
            for (int i = 0; i < limit; i++) {
                var ev = evidences.get(i);
                sb.append("- ");
                if (ev.fileName() != null) {
                    sb.append("(").append(ev.fileName());
                    if (ev.pageStart() != null) {
                        sb.append(", p.").append(ev.pageStart());
                        if (ev.pageEnd() != null && !ev.pageEnd().equals(ev.pageStart())) {
                            sb.append("-").append(ev.pageEnd());
                        }
                    }
                    sb.append(") ");
                }
                sb.append(ev.excerpt()).append("\n\n");
            }
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
            sb.append("[참고 자료] (").append(evidences.size()).append("건)\n");
            int limit = Math.min(5, evidences.size());
            for (int i = 0; i < limit; i++) {
                var ev = evidences.get(i);
                sb.append("- (");
                if (ev.fileName() != null) {
                    sb.append("파일명: ").append(ev.fileName());
                    if (ev.pageStart() != null) {
                        sb.append(", p.").append(ev.pageStart());
                        if (ev.pageEnd() != null && !ev.pageEnd().equals(ev.pageStart())) {
                            sb.append("-").append(ev.pageEnd());
                        }
                    }
                    sb.append(", ");
                }
                sb.append("유사도: ")
                        .append(String.format("%.3f", ev.score()))
                        .append(")\n")
                        .append(ev.excerpt()).append("\n\n");
            }
        }

        sb.append("[지시]\n");
        sb.append("참고 자료의 내용을 기반으로 가능한 한 구체적이고 실용적인 답변을 작성하라.\n");
        sb.append("참고 자료에 답변에 필요한 정보가 충분히 있으면 자신 있게 안내하라.\n");
        sb.append("정보가 부족한 부분에 대해서만 추가 확인을 요청하라.\n");
        sb.append("\"단정이 어렵다\", \"특정하기 어렵다\" 같은 모호한 표현을 지양하고, 근거가 있는 내용은 명확히 전달하라.\n\n");

        sb.append("[요구사항]\n");
        sb.append("1) 번호 인용([1], [2]) 금지. \"사내 자료를 참고한 결과\" 등 자연스러운 문맥 인용 사용\n");
        sb.append("2) 마크다운 서식(##, **, -, ```) 절대 금지. 순수 텍스트만 작성\n");
        sb.append("3) 이모지, 과도한 느낌표 금지\n");
        sb.append("4) 과장/단정 금지, 근거에 없는 내용 추측 금지\n");
        sb.append("5) 후속 확인 항목 1~3개 포함\n");
        sb.append("6) channel=email이면 인사(\"안녕하세요.\")/마무리(\"감사합니다.\") 포함, messenger면 [요약] 태그로 시작하여 간결하게\n");
        sb.append("7) 참고 자료의 내용을 인용할 때 해당 자료의 파일명과 페이지 번호를 괄호 안에 자연스럽게 표기할 것. 예: \"~기능이 제공됩니다 (10000107223.pdf, p.94-95)\". 근거가 없는 내용에는 출처 표기 금지\n");

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

    private static final String SYSTEM_PROMPT =
            "너는 Bio-Rad 고객 서비스팀의 한국어 비즈니스 이메일 작성 전문가이다.\n"
                    + "반드시 다음 규칙을 지켜라:\n"
                    + "1. 격식체 존댓말 사용 (~드립니다, ~바랍니다, ~겠습니다)\n"
                    + "2. email 채널: 인사(\"안녕하세요.\") → 맥락 → 본론 → 마무리(\"감사합니다.\")\n"
                    + "3. messenger 채널: [요약] 태그로 시작, 260자 이내, 간결하게\n"
                    + "4. 한 문장에 하나의 의미만 담아 짧고 명확하게 작성\n"
                    + "5. 마크다운 서식(##, **, -, 등) 절대 사용 금지. 순수 텍스트만 작성\n"
                    + "6. 각 주장의 근거가 되는 참고 자료의 출처를 본문 내에 자연스럽게 포함할 것. "
                    + "형식: (파일명, p.XX) 또는 (파일명, p.XX-YY). [1], [2] 같은 번호 인용은 금지\n"
                    + "7. 이모지, 과도한 느낌표 사용 금지\n"
                    + "8. 과장/단정 금지, 근거에 없는 내용 추측 금지";
}
