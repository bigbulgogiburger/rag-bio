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
        String channel,
        String reviewedBy,
        String reviewComment,
        String approvedBy,
        String approveComment,
        String sentBy,
        String sendChannel,
        String sendMessageId,
        List<String> formatWarnings,
        Integer reviewScore,
        String reviewDecision,
        String approvalDecision,
        String approvalReason,
        String translatedQuery,
        String previousAnswerId,
        int refinementCount,
        List<SelfReviewIssueResponse> selfReviewIssues
) {
    public record SelfReviewIssueResponse(
            String category,
            String severity,
            String description,
            String suggestion
    ) {}
}
