package com.biorad.csrag.interfaces.rest.dto.knowledge;

import java.util.List;

/**
 * Knowledge Base 문서 목록 응답 DTO
 */
public record KbDocumentListResponse(
    List<KbDocumentListItem> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public record KbDocumentListItem(
        java.util.UUID documentId,
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
        java.time.Instant createdAt
    ) {}
}
