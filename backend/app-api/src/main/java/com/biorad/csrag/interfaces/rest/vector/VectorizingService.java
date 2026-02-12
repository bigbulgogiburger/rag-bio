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

    public int upsertDocumentChunks(UUID documentId) {
        List<DocumentChunkJpaEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);

        for (DocumentChunkJpaEntity chunk : chunks) {
            List<Double> vector = embeddingService.embed(chunk.getContent());
            vectorStore.upsert(chunk.getId(), documentId, vector, chunk.getContent());
        }

        return chunks.size();
    }
}
