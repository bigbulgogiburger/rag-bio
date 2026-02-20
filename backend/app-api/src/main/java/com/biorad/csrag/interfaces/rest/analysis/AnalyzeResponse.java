package com.biorad.csrag.interfaces.rest.analysis;

import java.util.List;

public record AnalyzeResponse(
        String inquiryId,
        String verdict,
        double confidence,
        String reason,
        List<String> riskFlags,
        List<EvidenceItem> evidences,
        String translatedQuery
) {
}
