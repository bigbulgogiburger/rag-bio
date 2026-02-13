package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AnswerOrchestrationService {

    private final RetrieveStep retrieveStep;
    private final VerifyStep verifyStep;
    private final ComposeStep composeStep;
    private final OrchestrationRunJpaRepository runRepository;

    public AnswerOrchestrationService(
            RetrieveStep retrieveStep,
            VerifyStep verifyStep,
            ComposeStep composeStep,
            OrchestrationRunJpaRepository runRepository
    ) {
        this.retrieveStep = retrieveStep;
        this.verifyStep = verifyStep;
        this.composeStep = composeStep;
        this.runRepository = runRepository;
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel) {
        List<EvidenceItem> evidences = executeWithRunLog(inquiryId, "RETRIEVE", () -> retrieveStep.execute(inquiryId, question, 5));
        AnalyzeResponse analysis = executeWithRunLog(inquiryId, "VERIFY", () -> verifyStep.execute(inquiryId, question, evidences));
        ComposeStep.ComposeStepResult composed = executeWithRunLog(inquiryId, "COMPOSE", () -> composeStep.execute(analysis, tone, channel));
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
            throw ex;
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
