package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AnswerOrchestrationService {

    private final RetrieveStep retrieveStep;
    private final VerifyStep verifyStep;
    private final ComposeStep composeStep;

    public AnswerOrchestrationService(RetrieveStep retrieveStep, VerifyStep verifyStep, ComposeStep composeStep) {
        this.retrieveStep = retrieveStep;
        this.verifyStep = verifyStep;
        this.composeStep = composeStep;
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel) {
        List<EvidenceItem> evidences = retrieveStep.execute(inquiryId, question, 5);
        AnalyzeResponse analysis = verifyStep.execute(inquiryId, question, evidences);
        ComposeStep.ComposeStepResult composed = composeStep.execute(analysis, tone, channel);
        return new OrchestrationResult(analysis, composed.draft(), composed.formatWarnings());
    }

    public record OrchestrationResult(
            AnalyzeResponse analysis,
            String draft,
            List<String> formatWarnings
    ) {}
}
