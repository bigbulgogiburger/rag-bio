package com.biorad.csrag.interfaces.rest.feedback;

import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.infrastructure.persistence.feedback.AnswerFeedbackEntity;
import com.biorad.csrag.infrastructure.persistence.feedback.AnswerFeedbackRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(name = "Answer Feedback", description = "답변 피드백 API")
@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/answers/{answerId}/feedback")
public class AnswerFeedbackController {

    private static final Logger log = LoggerFactory.getLogger(AnswerFeedbackController.class);

    private static final Set<String> VALID_RATINGS = Set.of(
            "HELPFUL", "NOT_HELPFUL", "PARTIALLY_HELPFUL"
    );

    private final AnswerFeedbackRepository feedbackRepository;

    public AnswerFeedbackController(AnswerFeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    @Operation(summary = "피드백 제출", description = "답변에 대한 피드백을 제출합니다")
    @ApiResponse(responseCode = "201", description = "피드백 저장 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검증 실패 (잘못된 rating)")
    @PostMapping
    public ResponseEntity<FeedbackResponse> submitFeedback(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId,
            @Parameter(description = "답변 ID") @PathVariable Long answerId,
            @RequestBody FeedbackRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        log.info("feedback.submit inquiryId={} answerId={} rating={} userId={}",
                inquiryId, answerId, request.rating(), userId);

        if (request.rating() == null || !VALID_RATINGS.contains(request.rating())) {
            throw new ValidationException("INVALID_RATING",
                    "Rating must be one of: " + String.join(", ", VALID_RATINGS));
        }

        String issuesJson = request.issues() != null && !request.issues().isEmpty()
                ? "[" + String.join(",", request.issues().stream().map(s -> "\"" + s + "\"").toList()) + "]"
                : null;

        AnswerFeedbackEntity entity = new AnswerFeedbackEntity(
                inquiryId, answerId, request.rating(),
                issuesJson, request.comment(), userId
        );

        AnswerFeedbackEntity saved = feedbackRepository.save(entity);
        log.info("feedback.saved id={} inquiryId={} answerId={}", saved.getId(), inquiryId, answerId);

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @Operation(summary = "피드백 조회", description = "답변에 대한 피드백 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<List<FeedbackResponse>> getFeedback(
            @Parameter(description = "문의 ID") @PathVariable Long inquiryId,
            @Parameter(description = "답변 ID") @PathVariable Long answerId
    ) {
        log.info("feedback.list inquiryId={} answerId={}", inquiryId, answerId);

        List<FeedbackResponse> responses = feedbackRepository
                .findByInquiryIdAndAnswerId(inquiryId, answerId)
                .stream()
                .map(this::toResponse)
                .toList();

        return ResponseEntity.ok(responses);
    }

    private FeedbackResponse toResponse(AnswerFeedbackEntity entity) {
        List<String> issues = null;
        if (entity.getIssues() != null && !entity.getIssues().isBlank()) {
            // Simple JSON array parsing: remove brackets and quotes, split by comma
            String raw = entity.getIssues().replaceAll("[\\[\\]\"]", "");
            if (!raw.isBlank()) {
                issues = List.of(raw.split(","));
            }
        }

        return new FeedbackResponse(
                entity.getId(),
                entity.getRating(),
                issues,
                entity.getComment(),
                entity.getSubmittedBy(),
                entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : null
        );
    }

    public record FeedbackRequest(String rating, List<String> issues, String comment) {}

    public record FeedbackResponse(Long id, String rating, List<String> issues,
                                    String comment, String submittedBy, String createdAt) {}
}
