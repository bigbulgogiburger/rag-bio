package com.biorad.csrag.interfaces.rest.dto.knowledge;

import java.util.Map;

/**
 * Knowledge Base 통계 DTO
 */
public record KbStatsResponse(
    long totalDocuments,
    long indexedDocuments,
    long totalChunks,
    Map<String, Long> byCategory,
    Map<String, Long> byProductFamily
) {
}
