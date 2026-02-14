package com.biorad.csrag.interfaces.rest.dto.knowledge;

import java.time.Instant;
import java.util.UUID;

/**
 * Knowledge Base 문서 응답 DTO
 */
public record KbDocumentResponse(
    UUID documentId,
    String title,
    String category,
    String productFamily,
    String fileName,
    long fileSize,
    String status,
    Integer chunkCount,
    Integer vectorCount,
    String uploadedBy,
    String tags,
    String description,
    String lastError,
    Instant createdAt,
    Instant updatedAt
) {
}
