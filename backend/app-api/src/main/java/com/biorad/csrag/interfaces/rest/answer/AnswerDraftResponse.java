package com.biorad.csrag.interfaces.rest.answer;

import java.util.List;

public record AnswerDraftResponse(
        String inquiryId,
        String verdict,
        double confidence,
        String draft,
        List<String> citations,
        List<String> riskFlags
) {
}
