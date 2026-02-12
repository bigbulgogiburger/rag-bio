package com.biorad.csrag.interfaces.rest.document;

import java.time.Instant;

public record DocumentStatusResponse(
        String documentId,
        String inquiryId,
        String fileName,
        String contentType,
        long fileSize,
        String status,
        Instant createdAt,
        Instant updatedAt,
        String lastError,
        Double ocrConfidence
) {
}
