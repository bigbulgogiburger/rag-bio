package com.biorad.csrag.interfaces.rest.analysis;

public record EvidenceItem(
        String chunkId,
        String documentId,
        double score,
        String excerpt,
        String sourceType,    // "INQUIRY" 또는 "KNOWLEDGE_BASE"
        String fileName,      // 원본 문서 파일명 (nullable)
        Integer pageStart,    // PDF 시작 페이지 (nullable)
        Integer pageEnd       // PDF 끝 페이지 (nullable)
) {
}
