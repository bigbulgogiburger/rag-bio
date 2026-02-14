package com.biorad.csrag.interfaces.rest.dto.knowledge;

/**
 * Knowledge Base 일괄 인덱싱 결과 DTO
 */
public record KbBatchIndexingResponse(
    int processed,
    int succeeded,
    int failed
) {
}
