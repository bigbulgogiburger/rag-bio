package com.biorad.csrag.interfaces.rest.ops;

import java.util.List;

public record OpsMetricsResponse(
        long approvedOrSentCount,
        long sentCount,
        double sendSuccessRate,
        long fallbackDraftCount,
        long totalDraftCount,
        double fallbackDraftRate,
        List<FailureReasonCount> topFailureReasons
) {
    public record FailureReasonCount(String reason, long count) {}
}
