package com.biorad.csrag.interfaces.rest.answer.agent;

import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaRepository;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import com.biorad.csrag.common.exception.NotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReviewAgentService {

    private static final Logger log = LoggerFactory.getLogger(ReviewAgentService.class);
    private static final String AI_REVIEWER = "ai-review-agent";

    private final boolean openaiEnabled;
    private final RestClient restClient;
    private final String chatModel;
    private final ObjectMapper objectMapper;
    private final AnswerDraftJpaRepository answerDraftRepository;
    private final AiReviewResultJpaRepository aiReviewResultRepository;
    private final InquiryRepository inquiryRepository;
    private final PromptRegistry promptRegistry;

    public ReviewAgentService(
            @Value("${openai.enabled:false}") boolean openaiEnabled,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model.chat-heavy:gpt-5.2}") String chatModel,
            ObjectMapper objectMapper,
            AnswerDraftJpaRepository answerDraftRepository,
            AiReviewResultJpaRepository aiReviewResultRepository,
            InquiryRepository inquiryRepository,
            PromptRegistry promptRegistry
    ) {
        this.openaiEnabled = openaiEnabled;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
        this.answerDraftRepository = answerDraftRepository;
        this.aiReviewResultRepository = aiReviewResultRepository;
        this.inquiryRepository = inquiryRepository;
        this.promptRegistry = promptRegistry;

        if (openaiEnabled) {
            this.restClient = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .build();
        } else {
            this.restClient = null;
        }
    }

    public AiReviewResponse review(UUID inquiryId, UUID answerId) {
        AnswerDraftJpaEntity draft = answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        String customerQuestion = inquiryRepository.findById(new InquiryId(inquiryId))
                .map(Inquiry::getQuestion)
                .orElse("(질문 정보 없음)");

        ReviewResult result = openaiEnabled ? callOpenAi(draft, customerQuestion) : mockReview();

        String issuesJson;
        try {
            issuesJson = objectMapper.writeValueAsString(result.issues());
        } catch (JsonProcessingException e) {
            issuesJson = "[]";
        }

        AiReviewResultJpaEntity reviewEntity = new AiReviewResultJpaEntity(
                UUID.randomUUID(),
                answerId,
                inquiryId,
                result.decision(),
                result.score(),
                result.summary(),
                result.revisedDraft(),
                issuesJson,
                null,
                Instant.now()
        );
        aiReviewResultRepository.save(reviewEntity);

        draft.markAiReviewed(AI_REVIEWER, result.score(), result.decision(), result.summary());
        answerDraftRepository.save(draft);

        return new AiReviewResponse(
                result.decision(),
                result.score(),
                result.issues(),
                result.revisedDraft(),
                result.summary(),
                draft.getStatus(),
                AI_REVIEWER
        );
    }

    private ReviewResult mockReview() {
        return new ReviewResult(
                "PASS",
                85,
                List.of(),
                null,
                "Mock AI review: 답변 초안이 기준을 충족합니다."
        );
    }

    private ReviewResult callOpenAi(AnswerDraftJpaEntity draft, String customerQuestion) {
        try {
            String prompt = buildPrompt(draft, customerQuestion);

            String response = restClient.post()
                    .uri("/chat/completions")
                    .body(Map.of(
                            "model", chatModel,
                            "messages", new Object[]{
                                    Map.of("role", "system", "content", promptRegistry.get("review-agent")),
                                    Map.of("role", "user", "content", prompt)
                            },
                            "temperature", 0.1
                    ))
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response == null ? "{}" : response);
            String content = root.path("choices").path(0).path("message").path("content").asText("").trim();
            if (content.isBlank()) {
                throw new IllegalStateException("openai review empty content");
            }

            return parseReviewResponse(content);
        } catch (Exception ex) {
            log.warn("openai.review.failed -> fallback to mock: {}", ex.getMessage());
            return mockReview();
        }
    }

    private ReviewResult parseReviewResponse(String content) throws Exception {
        String cleaned = content;
        if (cleaned.contains("```json")) {
            cleaned = cleaned.substring(cleaned.indexOf("```json") + 7);
            if (cleaned.contains("```")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("```"));
            }
        }
        cleaned = cleaned.trim();

        JsonNode json = objectMapper.readTree(cleaned);

        String decision = json.path("decision").asText("PASS").toUpperCase();
        if (!List.of("PASS", "REVISE", "REJECT").contains(decision)) {
            decision = "PASS";
        }

        int score = json.path("score").asInt(85);
        score = Math.max(0, Math.min(100, score));

        String summary = json.path("summary").asText("AI 리뷰 결과입니다.");
        String revisedDraft = json.has("revisedDraft") && !json.path("revisedDraft").isNull()
                ? json.path("revisedDraft").asText()
                : null;

        List<ReviewIssue> issues = List.of();
        JsonNode issuesNode = json.path("issues");
        if (issuesNode.isArray()) {
            issues = objectMapper.convertValue(issuesNode, new TypeReference<>() {});
        }

        return new ReviewResult(decision, score, issues, revisedDraft, summary);
    }

    private String buildPrompt(AnswerDraftJpaEntity draft, String customerQuestion) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 고객 질문\n").append(customerQuestion).append("\n\n");
        sb.append("## 답변 초안 정보\n");
        sb.append("- Verdict: ").append(draft.getVerdict()).append("\n");
        sb.append("- Confidence: ").append(draft.getConfidence()).append("\n");
        sb.append("- Tone: ").append(draft.getTone()).append("\n");
        sb.append("- Channel: ").append(draft.getChannel()).append("\n\n");
        sb.append("## 답변 초안\n").append(draft.getDraft()).append("\n\n");
        sb.append("## 근거 자료 상세\n");
        String citations = draft.getCitations();
        if (citations != null && !citations.isBlank()) {
            String[] items = citations.split("\\s*\\|\\s*");
            for (int i = 0; i < items.length; i++) {
                sb.append("[").append(i + 1).append("] ").append(items[i].trim()).append("\n");
            }
        } else {
            sb.append("근거 자료 없음\n");
        }
        sb.append("\n## 리스크 플래그\n").append(draft.getRiskFlags()).append("\n\n");
        sb.append("## 지시사항\n");
        sb.append("위 답변 초안을 고객 질문 대비 검토하고, 아래 JSON 형식으로만 응답하라:\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"decision\": \"PASS | REVISE | REJECT\",\n");
        sb.append("  \"score\": 0~100,\n");
        sb.append("  \"summary\": \"리뷰 요약 (한국어, 2~3문장)\",\n");
        sb.append("  \"revisedDraft\": \"수정된 초안 (REVISE일 때만, 아니면 null)\",\n");
        sb.append("  \"issues\": [\n");
        sb.append("    {\n");
        sb.append("      \"category\": \"ACCURACY | COMPLETENESS | CITATION | HALLUCINATION | TONE | RISK | FORMAT\",\n");
        sb.append("      \"severity\": \"CRITICAL | HIGH | MEDIUM | LOW\",\n");
        sb.append("      \"description\": \"이슈 설명\",\n");
        sb.append("      \"suggestion\": \"개선 제안\"\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        sb.append("```\n");
        return sb.toString();
    }

}
