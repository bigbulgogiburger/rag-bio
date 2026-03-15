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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    private final int enrichmentMinParents;
    private final int enrichmentMaxParents;
    private final int enrichmentSampleInterval;

    @Autowired
    public VectorizingService(
            DocumentChunkJpaRepository chunkRepository,
            DocumentMetadataJpaRepository docMetadataRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            ContextualChunkEnricher contextualChunkEnricher,
            RagMetricsService ragMetricsService,
            @Value("${rag.indexing.enrichment-min-parents:5}") int enrichmentMinParents,
            @Value("${rag.indexing.enrichment-max-parents:30}") int enrichmentMaxParents,
            @Value("${rag.indexing.enrichment-sample-interval:3}") int enrichmentSampleInterval
    ) {
        this.chunkRepository = chunkRepository;
        this.docMetadataRepository = docMetadataRepository;
        this.kbDocRepository = kbDocRepository;
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.contextualChunkEnricher = contextualChunkEnricher;
        this.ragMetricsService = ragMetricsService;
        this.enrichmentMinParents = enrichmentMinParents;
        this.enrichmentMaxParents = enrichmentMaxParents;
        this.enrichmentSampleInterval = enrichmentSampleInterval;
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
        this.enrichmentMinParents = 5;
        this.enrichmentMaxParents = 30;
        this.enrichmentSampleInterval = 3;
    }

    /** 하위 호환 테스트용 생성자 (docMetadataRepository 없이) */
    public VectorizingService(
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
        this.enrichmentMinParents = 5;
        this.enrichmentMaxParents = 30;
        this.enrichmentSampleInterval = 3;
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

        // 선택적 Contextual Enrichment: Parent 청크 수에 따라 전략 결정
        applySelectiveEnrichment(documentId, chunks, documentText, fileName);

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

    /**
     * Parent 청크 수에 따라 enrichment 전략을 결정하고 적용한다.
     *
     * - < enrichmentMinParents (기본 5): SKIP (짧은 문서, 전체 문맥이 이미 보임)
     * - enrichmentMinParents ~ enrichmentMaxParents (기본 5~30): FULL (모든 parent enrichment)
     * - > enrichmentMaxParents (기본 30): SAMPLE (매 N번째 parent만 enrichment)
     */
    public void applySelectiveEnrichment(UUID documentId, List<DocumentChunkJpaEntity> chunks,
                                         String documentText, String fileName) {
        if (chunks == null || chunks.isEmpty()) return;

        // Parent 청크 수 계산
        List<DocumentChunkJpaEntity> parents = chunks.stream()
                .filter(c -> "PARENT".equals(c.getChunkLevel()))
                .toList();

        // flat 구조 (parent-child 분리 없음)인 경우 항상 FULL enrichment
        // 선택적 enrichment는 parent-child 계층이 있는 경우에만 적용
        if (parents.isEmpty()) {
            log.info("Enrichment strategy for doc {}: FULL (flat structure, {} chunks)", documentId, chunks.size());
            contextualChunkEnricher.enrichChunks(documentText, chunks, fileName);
            return;
        }

        int parentCount = parents.size();
        String strategy;

        if (parentCount < enrichmentMinParents) {
            // SKIP: 짧은 문서 — enrichment 불필요, enrichedContent = content 설정
            strategy = "SKIP";
            for (DocumentChunkJpaEntity chunk : chunks) {
                chunk.setEnrichedContent(chunk.getContent());
            }
            log.info("Enrichment strategy for doc {}: {} ({} parents) — skipping LLM enrichment",
                    documentId, strategy, parentCount);
        } else if (parentCount <= enrichmentMaxParents) {
            // FULL: 모든 parent 대상 enrichment
            strategy = "FULL";
            log.info("Enrichment strategy for doc {}: {} ({} parents)", documentId, strategy, parentCount);
            contextualChunkEnricher.enrichChunks(documentText, chunks, fileName);
        } else {
            // SAMPLE: 매 N번째 parent만 enrichment, 나머지는 content 그대로
            strategy = "SAMPLE";
            log.info("Enrichment strategy for doc {}: {} ({} parents, interval={})",
                    documentId, strategy, parentCount, enrichmentSampleInterval);

            // 샘플링할 parent만 선별
            List<DocumentChunkJpaEntity> sampledParents = new ArrayList<>();
            List<DocumentChunkJpaEntity> skippedParents = new ArrayList<>();
            List<DocumentChunkJpaEntity> actualParents = parents.isEmpty() ? chunks : parents;

            for (int i = 0; i < actualParents.size(); i++) {
                if (i % enrichmentSampleInterval == 0) {
                    sampledParents.add(actualParents.get(i));
                } else {
                    skippedParents.add(actualParents.get(i));
                }
            }

            // 스킵된 parent는 enrichedContent = content
            for (DocumentChunkJpaEntity skipped : skippedParents) {
                skipped.setEnrichedContent(skipped.getContent());
            }

            // 샘플링된 parent + 관련 child만 enrichment 대상으로 구성
            // enrichChunks는 전체 리스트를 받아야 parent-child 관계를 처리하므로,
            // 샘플링된 parent의 ID set을 만들고 해당 child만 포함
            var sampledParentIds = sampledParents.stream()
                    .map(DocumentChunkJpaEntity::getId)
                    .collect(java.util.stream.Collectors.toSet());

            List<DocumentChunkJpaEntity> chunksToEnrich = new ArrayList<>(sampledParents);
            for (DocumentChunkJpaEntity chunk : chunks) {
                if ("CHILD".equals(chunk.getChunkLevel()) && chunk.getParentChunkId() != null) {
                    if (sampledParentIds.contains(chunk.getParentChunkId())) {
                        chunksToEnrich.add(chunk);
                    } else {
                        // 스킵된 parent의 child → enrichedContent = content
                        chunk.setEnrichedContent(chunk.getContent());
                    }
                }
            }

            if (!chunksToEnrich.isEmpty()) {
                contextualChunkEnricher.enrichChunks(documentText, chunksToEnrich, fileName);
            }
        }
    }
}
