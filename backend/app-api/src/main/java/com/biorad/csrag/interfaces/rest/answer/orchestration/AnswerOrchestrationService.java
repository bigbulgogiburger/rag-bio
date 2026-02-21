package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnswerOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerOrchestrationService.class);
    private static final int MAX_RECOMPOSE_ATTEMPTS = 2;

    private final RetrieveStep retrieveStep;
    private final VerifyStep verifyStep;
    private final ComposeStep composeStep;
    private final SelfReviewStep selfReviewStep;
    private final OrchestrationRunJpaRepository runRepository;
    private final SseService sseService;

    public AnswerOrchestrationService(
            RetrieveStep retrieveStep,
            VerifyStep verifyStep,
            ComposeStep composeStep,
            SelfReviewStep selfReviewStep,
            OrchestrationRunJpaRepository runRepository,
            SseService sseService
    ) {
        this.retrieveStep = retrieveStep;
        this.verifyStep = verifyStep;
        this.composeStep = composeStep;
        this.selfReviewStep = selfReviewStep;
        this.runRepository = runRepository;
        this.sseService = sseService;
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel) {
        return run(inquiryId, question, tone, channel, null, null);
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel,
                                    String additionalInstructions, String previousAnswerDraft) {
        emitPipelineEvent(inquiryId, "RETRIEVE", "STARTED", null);
        List<EvidenceItem> evidences = executeWithRunLog(inquiryId, "RETRIEVE", () -> retrieveStep.execute(inquiryId, question, 5));
        emitPipelineEvent(inquiryId, "RETRIEVE", "COMPLETED", null);

        emitPipelineEvent(inquiryId, "VERIFY", "STARTED", null);
        AnalyzeResponse analysis = executeWithRunLog(inquiryId, "VERIFY", () -> verifyStep.execute(inquiryId, question, evidences));
        emitPipelineEvent(inquiryId, "VERIFY", "COMPLETED", null);

        emitPipelineEvent(inquiryId, "COMPOSE", "STARTED", null);
        ComposeStep.ComposeStepResult composed = executeWithRunLog(inquiryId, "COMPOSE",
                () -> composeStep.execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft));
        emitPipelineEvent(inquiryId, "COMPOSE", "COMPLETED", null);

        // Self-review with retry loop
        String finalDraft = composed.draft();
        List<String> finalWarnings = composed.formatWarnings();
        List<SelfReviewStep.QualityIssue> selfReviewIssues = List.of();

        emitPipelineEvent(inquiryId, "SELF_REVIEW", "STARTED", null);
        try {
            SelfReviewStep.SelfReviewResult reviewResult = executeWithRunLog(
                    inquiryId, "SELF_REVIEW",
                    () -> selfReviewStep.review(composed.draft(), analysis.evidences(), question)
            );

            if (reviewResult.passed()) {
                selfReviewIssues = reviewResult.issues();
            } else {
                String currentDraft = composed.draft();
                SelfReviewStep.SelfReviewResult latestReview = reviewResult;

                for (int attempt = 1; attempt <= MAX_RECOMPOSE_ATTEMPTS; attempt++) {
                    log.info("self-review retry attempt={} inquiryId={}", attempt, inquiryId);
                    emitPipelineEvent(inquiryId, "SELF_REVIEW", "RETRY",
                            "재작성 시도 " + attempt + "/" + MAX_RECOMPOSE_ATTEMPTS);

                    String feedback = latestReview.feedback();
                    ComposeStep.ComposeStepResult recomposed = composeStep.execute(
                            analysis, tone, channel, feedback, currentDraft);

                    latestReview = selfReviewStep.review(recomposed.draft(), analysis.evidences(), question);
                    currentDraft = recomposed.draft();
                    finalWarnings = recomposed.formatWarnings();

                    if (latestReview.passed()) {
                        break;
                    }
                }

                finalDraft = currentDraft;
                selfReviewIssues = latestReview.issues();

                if (!latestReview.passed()) {
                    finalWarnings = new ArrayList<>(finalWarnings);
                    finalWarnings.add("SELF_REVIEW_INCOMPLETE");
                    log.warn("self-review did not pass after {} retries inquiryId={}",
                            MAX_RECOMPOSE_ATTEMPTS, inquiryId);
                }
            }
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "COMPLETED", null);
        } catch (Exception e) {
            log.warn("self-review failed, using original draft inquiryId={}", inquiryId, e);
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "FAILED", e.getMessage());
            // Fall through with original draft - self-review is non-blocking
        }

        return new OrchestrationResult(analysis, finalDraft, finalWarnings, selfReviewIssues);
    }

    private <T> T executeWithRunLog(UUID inquiryId, String step, StepSupplier<T> supplier) {
        long started = System.currentTimeMillis();
        try {
            T result = supplier.get();
            runRepository.save(new OrchestrationRunJpaEntity(
                    UUID.randomUUID(), inquiryId, step, "SUCCESS",
                    System.currentTimeMillis() - started, null, Instant.now()
            ));
            return result;
        } catch (RuntimeException ex) {
            runRepository.save(new OrchestrationRunJpaEntity(
                    UUID.randomUUID(), inquiryId, step, "FAILED",
                    System.currentTimeMillis() - started,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    Instant.now()
            ));
            emitPipelineEvent(inquiryId, step, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private void emitPipelineEvent(UUID inquiryId, String step, String status, String error) {
        try {
            sseService.send(inquiryId, "pipeline-step", Map.of(
                    "step", step,
                    "status", status,
                    "error", error == null ? "" : error
            ));
        } catch (Exception e) {
            log.debug("sse.emit.failed inquiryId={} event=pipeline-step step={}", inquiryId, step);
        }
    }

    @FunctionalInterface
    private interface StepSupplier<T> {
        T get();
    }

    public record OrchestrationResult(
            AnalyzeResponse analysis,
            String draft,
            List<String> formatWarnings,
            List<SelfReviewStep.QualityIssue> selfReviewIssues
    ) {
        public OrchestrationResult(AnalyzeResponse analysis, String draft, List<String> formatWarnings) {
            this(analysis, draft, formatWarnings, List.of());
        }
    }
}
