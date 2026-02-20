package com.biorad.csrag.interfaces.rest.ops;

import java.util.List;

public record KbUsageResponse(
        String period,
        String from,
        String to,
        long totalEvidences,
        long kbEvidences,
        double kbUsageRate,
        List<TopDocument> topDocuments
) {
    public record TopDocument(
            String documentId,
            String fileName,
            long referenceCount
    ) {}
}
