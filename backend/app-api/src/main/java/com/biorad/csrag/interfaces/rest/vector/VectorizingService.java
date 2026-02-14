package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VectorizingService {

    private final DocumentChunkJpaRepository chunkRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public VectorizingService(
            DocumentChunkJpaRepository chunkRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
    }

    /**
     * 문서의 청크들을 벡터화하여 저장
     *
     * @param documentId 문서 ID
     * @return 벡터화된 청크 수
     */
    public int upsertDocumentChunks(UUID documentId) {
        List<DocumentChunkJpaEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);

        for (DocumentChunkJpaEntity chunk : chunks) {
            List<Double> vector = embeddingService.embed(chunk.getContent());
            String sourceType = chunk.getSourceType() != null ? chunk.getSourceType() : "INQUIRY";
            vectorStore.upsert(chunk.getId(), documentId, vector, chunk.getContent(), sourceType);
        }

        return chunks.size();
    }
}
