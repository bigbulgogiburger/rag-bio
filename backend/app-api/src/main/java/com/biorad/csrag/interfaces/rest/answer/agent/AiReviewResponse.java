package com.biorad.csrag.interfaces.rest.answer.agent;

import java.util.List;

public record AiReviewResponse(
        String decision,
        int score,
        List<ReviewIssue> issues,
        String revisedDraft,
        String summary,
        String status,
        String reviewedBy
) {
}
