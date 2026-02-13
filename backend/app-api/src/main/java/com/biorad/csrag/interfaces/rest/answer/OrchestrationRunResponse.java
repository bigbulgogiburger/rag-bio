package com.biorad.csrag.interfaces.rest.answer;

import java.time.Instant;

public record OrchestrationRunResponse(
        String runId,
        String inquiryId,
        String step,
        String status,
        long latencyMs,
        String errorMessage,
        Instant createdAt
) {
}
