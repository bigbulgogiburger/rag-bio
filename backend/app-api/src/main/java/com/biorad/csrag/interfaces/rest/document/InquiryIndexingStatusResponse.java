package com.biorad.csrag.interfaces.rest.document;

import java.util.List;

public record InquiryIndexingStatusResponse(
        String inquiryId,
        int total,
        int uploaded,
        int parsing,
        int parsed,
        int chunked,
        int failed,
        List<DocumentStatusResponse> documents
) {
}
