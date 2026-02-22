package com.biorad.csrag.interfaces.rest.analysis;

public record EvidenceItem(
        String chunkId,
        String documentId,
        double score,
        String excerpt,
        String sourceType,    // "INQUIRY" 또는 "KNOWLEDGE_BASE"
        String fileName,      // 원본 문서 파일명 (nullable)
        Integer pageStart,    // PDF 시작 페이지 (nullable)
        Integer pageEnd,      // PDF 끝 페이지 (nullable)
        String productFamily  // 제품 패밀리 (nullable, 예: "naica", "QX200")
) {
    /** 하위 호환: productFamily 없이 8-arg 생성자 */
    public EvidenceItem(String chunkId, String documentId, double score, String excerpt,
                        String sourceType, String fileName, Integer pageStart, Integer pageEnd) {
        this(chunkId, documentId, score, excerpt, sourceType, fileName, pageStart, pageEnd, null);
    }
}
