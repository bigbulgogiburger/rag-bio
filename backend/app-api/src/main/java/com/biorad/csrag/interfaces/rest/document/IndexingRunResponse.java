package com.biorad.csrag.interfaces.rest.document;

public record IndexingRunResponse(
        String inquiryId,
        int processed,
        int succeeded,
        int failed
) {
}
