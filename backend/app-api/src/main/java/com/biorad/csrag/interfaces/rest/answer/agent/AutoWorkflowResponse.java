package com.biorad.csrag.interfaces.rest.answer.agent;

public record AutoWorkflowResponse(
        AiReviewResponse review,
        AiApprovalResponse approval,
        String finalStatus,
        boolean requiresHumanAction,
        String summary
) {
}
