package com.biorad.csrag.interfaces.rest.document;

public record DocumentUploadResponse(
        String documentId,
        String inquiryId,
        String fileName,
        String status
) {
}
