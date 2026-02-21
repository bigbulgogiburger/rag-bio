package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;

import java.util.List;

public interface ComposeStep {
    ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel);

    default ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel,
                                      String additionalInstructions, String previousAnswerDraft) {
        return execute(analysis, tone, channel);
    }

    record ComposeStepResult(String draft, List<String> formatWarnings) {}
}
