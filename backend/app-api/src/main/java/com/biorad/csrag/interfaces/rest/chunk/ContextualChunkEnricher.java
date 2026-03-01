package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;

import java.util.List;

public interface ContextualChunkEnricher {

    /**
     * 청크 목록에 문서 문맥을 주입하여 enrichedContent 설정.
     * Parent 청크에 대해 LLM으로 context prefix 생성 후 Child에 상속.
     *
     * @param documentText 전체 문서 텍스트 (컨텍스트 파악용)
     * @param chunks 청킹된 chunk 엔티티 목록 (parent + child 모두 포함)
     * @param fileName 문서 파일명 (컨텍스트 힌트)
     */
    void enrichChunks(String documentText, List<DocumentChunkJpaEntity> chunks, String fileName);
}
