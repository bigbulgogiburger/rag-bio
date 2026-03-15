package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;

import java.util.List;
import java.util.function.Consumer;

public interface ComposeStep {
    ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel);

    default ComposeStepResult execute(AnalyzeResponse analysis, String tone, String channel,
                                      String additionalInstructions, String previousAnswerDraft) {
        return execute(analysis, tone, channel);
    }

    /**
     * 스트리밍 방식 답변 생성 — 토큰 단위 콜백을 통해 실시간 전송.
     * 기본 구현은 기존 블로킹 방식으로 fallback (Mock용).
     */
    default ComposeStepResult executeStreaming(
            AnalyzeResponse analysis, String tone, String channel,
            String additionalInstructions, String previousAnswerDraft,
            Consumer<String> onToken) {
        return execute(analysis, tone, channel, additionalInstructions, previousAnswerDraft);
    }

    record ComposeStepResult(String draft, List<String> formatWarnings) {}
}
