package com.biorad.csrag.interfaces.rest.ops;

import java.util.List;

public record OpsMetricsResponse(
        long approvedOrSentCount,
        long sentCount,
        double sendSuccessRate,
        long duplicateBlockedCount,
        long totalSendAttemptCount,
        double duplicateBlockRate,
        long fallbackDraftCount,
        long totalDraftCount,
        double fallbackDraftRate,
        List<FailureReasonCount> topFailureReasons
) {
    public record FailureReasonCount(String reason, long count) {}
}
