package com.biorad.csrag.interfaces.rest.answer;

import java.util.List;

public record AnswerDraftResponse(
        String answerId,
        String inquiryId,
        int version,
        String status,
        String verdict,
        double confidence,
        String draft,
        List<String> citations,
        List<String> riskFlags,
        String tone,
        String channel
) {
}
