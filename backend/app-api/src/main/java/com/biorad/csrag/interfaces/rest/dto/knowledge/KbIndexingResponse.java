package com.biorad.csrag.interfaces.rest.dto.knowledge;

import java.util.UUID;

/**
 * Knowledge Base 인덱싱 결과 DTO
 */
public record KbIndexingResponse(
    UUID documentId,
    String status,
    int chunkCount,
    int vectorCount
) {
}
