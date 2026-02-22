package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VectorizingService {

    private final DocumentChunkJpaRepository chunkRepository;
    private final KnowledgeDocumentJpaRepository kbDocRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;

    public VectorizingService(
            DocumentChunkJpaRepository chunkRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore
    ) {
        this.chunkRepository = chunkRepository;
        this.kbDocRepository = kbDocRepository;
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
        // 기존 벡터 삭제 후 재생성 (ChunkingService가 새 UUID로 청크를 생성하므로 기존 벡터가 고아가 됨)
        vectorStore.deleteByDocumentId(documentId);

        List<DocumentChunkJpaEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);

        // KB 문서인 경우 부모 문서에서 productFamily 조회 (ChunkingService가 청크에 설정하지 않으므로)
        String resolvedProductFamily = null;
        if (!chunks.isEmpty() && "KNOWLEDGE_BASE".equals(chunks.get(0).getSourceType())) {
            resolvedProductFamily = kbDocRepository.findById(documentId)
                    .map(KnowledgeDocumentJpaEntity::getProductFamily)
                    .orElse(null);
        }

        for (DocumentChunkJpaEntity chunk : chunks) {
            List<Double> vector = embeddingService.embed(chunk.getContent());
            String sourceType = chunk.getSourceType() != null ? chunk.getSourceType() : "INQUIRY";
            String productFamily = resolvedProductFamily != null ? resolvedProductFamily : chunk.getProductFamily();
            vectorStore.upsert(chunk.getId(), documentId, vector, chunk.getContent(), sourceType, productFamily);
        }

        return chunks.size();
    }
}
