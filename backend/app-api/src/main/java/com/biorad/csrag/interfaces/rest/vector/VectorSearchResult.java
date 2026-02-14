package com.biorad.csrag.interfaces.rest.vector;

import java.util.UUID;

public record VectorSearchResult(
        UUID chunkId,
        UUID documentId,
        String content,
        double score,
        String sourceType    // "INQUIRY" 또는 "KNOWLEDGE_BASE"
) {
}
