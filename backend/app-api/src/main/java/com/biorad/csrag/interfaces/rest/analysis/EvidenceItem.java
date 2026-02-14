package com.biorad.csrag.interfaces.rest.analysis;

public record EvidenceItem(
        String chunkId,
        String documentId,
        double score,
        String excerpt,
        String sourceType    // "INQUIRY" 또는 "KNOWLEDGE_BASE"
) {
}
