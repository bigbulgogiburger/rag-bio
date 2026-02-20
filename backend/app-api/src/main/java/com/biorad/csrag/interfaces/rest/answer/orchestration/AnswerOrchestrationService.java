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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AnswerOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerOrchestrationService.class);

    private final RetrieveStep retrieveStep;
    private final VerifyStep verifyStep;
    private final ComposeStep composeStep;
    private final OrchestrationRunJpaRepository runRepository;
    private final SseService sseService;

    public AnswerOrchestrationService(
            RetrieveStep retrieveStep,
            VerifyStep verifyStep,
            ComposeStep composeStep,
            OrchestrationRunJpaRepository runRepository,
            SseService sseService
    ) {
        this.retrieveStep = retrieveStep;
        this.verifyStep = verifyStep;
        this.composeStep = composeStep;
        this.runRepository = runRepository;
        this.sseService = sseService;
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel) {
        emitPipelineEvent(inquiryId, "RETRIEVE", "STARTED", null);
        List<EvidenceItem> evidences = executeWithRunLog(inquiryId, "RETRIEVE", () -> retrieveStep.execute(inquiryId, question, 5));
        emitPipelineEvent(inquiryId, "RETRIEVE", "COMPLETED", null);

        emitPipelineEvent(inquiryId, "VERIFY", "STARTED", null);
        AnalyzeResponse analysis = executeWithRunLog(inquiryId, "VERIFY", () -> verifyStep.execute(inquiryId, question, evidences));
        emitPipelineEvent(inquiryId, "VERIFY", "COMPLETED", null);

        emitPipelineEvent(inquiryId, "COMPOSE", "STARTED", null);
        ComposeStep.ComposeStepResult composed = executeWithRunLog(inquiryId, "COMPOSE", () -> composeStep.execute(analysis, tone, channel));
        emitPipelineEvent(inquiryId, "COMPOSE", "COMPLETED", null);

        return new OrchestrationResult(analysis, composed.draft(), composed.formatWarnings());
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
            List<String> formatWarnings
    ) {}
}
