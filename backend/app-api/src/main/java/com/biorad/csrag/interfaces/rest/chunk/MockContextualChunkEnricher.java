package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MockContextualChunkEnricher implements ContextualChunkEnricher {

    /**
     * Mock: enrichedContent = content (LLM context 생성 없이 원본 사용)
     */
    @Override
    public void enrichChunks(String documentText, List<DocumentChunkJpaEntity> chunks, String fileName) {
        if (chunks == null) return;
        for (DocumentChunkJpaEntity chunk : chunks) {
            if (chunk.getEnrichedContent() == null) {
                chunk.setEnrichedContent(chunk.getContent());
            }
        }
    }
}
