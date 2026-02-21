package com.biorad.csrag.interfaces.rest.answer;

import java.util.List;

public record AnswerHistoryDetailResponse(
    AnswerDraftResponse answer,
    List<AiReviewHistoryItem> aiReviewHistory
) {
    public record AiReviewHistoryItem(
        String reviewId,
        String decision,
        int score,
        String summary,
        String revisedDraft,
        List<ReviewIssueItem> issues,
        List<GateResultItem> gateResults,
        String createdAt
    ) {}

    public record ReviewIssueItem(
        String category,
        String severity,
        String description,
        String suggestion
    ) {}

    public record GateResultItem(
        String gate,
        boolean passed,
        String actualValue,
        String threshold
    ) {}
}
