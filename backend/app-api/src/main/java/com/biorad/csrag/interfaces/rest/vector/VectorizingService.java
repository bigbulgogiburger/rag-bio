package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.application.ops.RagMetricsService;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ContextualChunkEnricher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class VectorizingService {

    private static final Logger log = LoggerFactory.getLogger(VectorizingService.class);

    private final DocumentChunkJpaRepository chunkRepository;
    private final DocumentMetadataJpaRepository docMetadataRepository;
    private final KnowledgeDocumentJpaRepository kbDocRepository;
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final ContextualChunkEnricher contextualChunkEnricher;
    private final RagMetricsService ragMetricsService;

    @Autowired
    public VectorizingService(
            DocumentChunkJpaRepository chunkRepository,
            DocumentMetadataJpaRepository docMetadataRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ContextualChunkEnricher contextualChunkEnricher,
            RagMetricsService ragMetricsService
    ) {
        this.chunkRepository = chunkRepository;
        this.docMetadataRepository = docMetadataRepository;
        this.kbDocRepository = kbDocRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.contextualChunkEnricher = contextualChunkEnricher;
        this.ragMetricsService = ragMetricsService;
    }

    /** 테스트용 생성자 (docMetadataRepository 포함) */
    VectorizingService(
            DocumentChunkJpaRepository chunkRepository,
            DocumentMetadataJpaRepository docMetadataRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ContextualChunkEnricher contextualChunkEnricher
    ) {
        this.chunkRepository = chunkRepository;
        this.docMetadataRepository = docMetadataRepository;
        this.kbDocRepository = kbDocRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.contextualChunkEnricher = contextualChunkEnricher;
        this.ragMetricsService = null;
    }

    /** 하위 호환 테스트용 생성자 (docMetadataRepository 없이) */
    VectorizingService(
            DocumentChunkJpaRepository chunkRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ContextualChunkEnricher contextualChunkEnricher,
            RagMetricsService ragMetricsService
    ) {
        this.chunkRepository = chunkRepository;
        this.docMetadataRepository = null;
        this.kbDocRepository = kbDocRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.contextualChunkEnricher = contextualChunkEnricher;
        this.ragMetricsService = ragMetricsService;
    }

    /**
     * 문서의 청크들을 벡터화하여 저장
     *
     * @param documentId 문서 ID
     * @return 벡터화된 청크 수
     */
    public int upsertDocumentChunks(UUID documentId) {
        long indexingStart = System.currentTimeMillis();
        // 기존 벡터 삭제 후 재생성 (ChunkingService가 새 UUID로 청크를 생성하므로 기존 벡터가 고아가 됨)
        vectorStore.deleteByDocumentId(documentId);

        List<DocumentChunkJpaEntity> chunks = chunkRepository.findByDocumentIdOrderByChunkIndexAsc(documentId);

        // 문서 메타데이터 조회: productFamily + extractedText + fileName (Contextual Enrichment에 필요)
        String resolvedProductFamily = null;
        String documentText = "";
        String fileName = "";
        if (!chunks.isEmpty()) {
            String sourceType = chunks.get(0).getSourceType();
            if ("KNOWLEDGE_BASE".equals(sourceType)) {
                // KB 문서: 한 번의 조회로 productFamily, extractedText, fileName 모두 취득
                var kbDoc = kbDocRepository.findById(documentId).orElse(null);
                if (kbDoc != null) {
                    resolvedProductFamily = kbDoc.getProductFamily();
                    documentText = kbDoc.getExtractedText() != null ? kbDoc.getExtractedText() : "";
                    fileName = kbDoc.getFileName() != null ? kbDoc.getFileName() : "";
                }
            } else if (docMetadataRepository != null) {
                // INQUIRY 문서
                var doc = docMetadataRepository.findById(documentId).orElse(null);
                if (doc != null) {
                    documentText = doc.getExtractedText() != null ? doc.getExtractedText() : "";
                    fileName = doc.getFileName() != null ? doc.getFileName() : "";
                }
            }
            log.debug("contextual enrichment: docId={}, sourceType={}, textLength={}, fileName='{}'",
                    documentId, sourceType, documentText.length(), fileName);
        }

        // Contextual enrichment: enrichedContent에 문맥 주입
        contextualChunkEnricher.enrichChunks(documentText, chunks, fileName);

        // CHILD 청크만 임베딩 (PARENT는 검색 대상 아님). flat 구조면 전부 임베딩.
        List<DocumentChunkJpaEntity> chunksToEmbed = chunks.stream()
                .filter(c -> !"PARENT".equals(c.getChunkLevel()))
                .toList();

        // 배치 임베딩 (50개씩) — enrichedContent 사용
        int batchSize = 50;
        for (int i = 0; i < chunksToEmbed.size(); i += batchSize) {
            List<DocumentChunkJpaEntity> batch = chunksToEmbed.subList(i, Math.min(i + batchSize, chunksToEmbed.size()));
            List<String> texts = batch.stream()
                    .map(c -> c.getEnrichedContent() != null ? c.getEnrichedContent() : c.getContent())
                    .toList();
            List<List<Double>> vectors = embeddingService.embedBatch(texts);

            for (int j = 0; j < batch.size(); j++) {
                DocumentChunkJpaEntity chunk = batch.get(j);
                String sourceType = chunk.getSourceType() != null ? chunk.getSourceType() : "INQUIRY";
                String productFamily = resolvedProductFamily != null ? resolvedProductFamily : chunk.getProductFamily();
                vectorStore.upsert(chunk.getId(), documentId, vectors.get(j), chunk.getContent(), sourceType, productFamily);
            }
        }

        if (ragMetricsService != null) ragMetricsService.record(null, "INDEXING_TIME", System.currentTimeMillis() - indexingStart);
        return chunks.size();
    }
}
