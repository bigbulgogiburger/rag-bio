package com.biorad.csrag.application.knowledge;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeBaseSpecifications;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.dto.knowledge.*;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Knowledge Base 관리 서비스
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private final KnowledgeDocumentJpaRepository kbDocRepository;
    private final DocumentChunkJpaRepository chunkRepository;
    private final VectorStore vectorStore;
    private final DocumentMetadataAnalyzer metadataAnalyzer;
    private final KnowledgeIndexingWorker indexingWorker;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public KnowledgeBaseService(
            KnowledgeDocumentJpaRepository kbDocRepository,
            DocumentChunkJpaRepository chunkRepository,
            VectorStore vectorStore,
            DocumentMetadataAnalyzer metadataAnalyzer,
            KnowledgeIndexingWorker indexingWorker
    ) {
        this.kbDocRepository = kbDocRepository;
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
        this.metadataAnalyzer = metadataAnalyzer;
        this.indexingWorker = indexingWorker;
    }

    /**
     * 문서 업로드
     */
    @Transactional
    public KbDocumentResponse upload(
            MultipartFile file,
            String title,
            String category,
            String productFamily,
            String description,
            String tags,
            String uploadedBy
    ) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        try {
            // 파일 저장
            Path kbDir = Paths.get(uploadDir, "knowledge-base");
            Files.createDirectories(kbDir);

            UUID docId = UUID.randomUUID();
            String fileName = file.getOriginalFilename();
            String storagePath = kbDir.resolve(docId + "_" + fileName).toString();
            Files.write(Paths.get(storagePath), file.getBytes());

            // 엔티티 생성
            KnowledgeDocumentJpaEntity entity = KnowledgeDocumentJpaEntity.create(
                    title,
                    category,
                    productFamily,
                    fileName,
                    file.getContentType(),
                    file.getSize(),
                    storagePath,
                    description,
                    tags,
                    uploadedBy
            );

            kbDocRepository.save(entity);
            log.info("kb.upload.success documentId={} title={}", entity.getId(), title);

            // AI 메타데이터 분석: 빈 필드가 있으면 자동 채움
            enrichWithAi(entity, Paths.get(storagePath), file.getContentType());

            return toResponse(entity);
        } catch (IOException e) {
            log.error("kb.upload.failed title={} error={}", title, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "File upload failed");
        }
    }

    /**
     * 문서 목록 조회
     */
    @Transactional(readOnly = true)
    public KbDocumentListResponse list(
            String category,
            String productFamily,
            String status,
            String keyword,
            Pageable pageable
    ) {
        Page<KnowledgeDocumentJpaEntity> page = kbDocRepository.findAll(
                KnowledgeBaseSpecifications.withFilters(category, productFamily, status, keyword),
                pageable
        );

        List<KbDocumentListResponse.KbDocumentListItem> items = page.getContent().stream()
                .map(e -> new KbDocumentListResponse.KbDocumentListItem(
                        e.getId(),
                        e.getTitle(),
                        e.getCategory(),
                        e.getProductFamily(),
                        e.getFileName(),
                        e.getFileSize(),
                        e.getStatus(),
                        e.getChunkCount(),
                        e.getVectorCount(),
                        e.getUploadedBy(),
                        e.getTags(),
                        e.getCreatedAt()
                ))
                .toList();

        return new KbDocumentListResponse(
                items,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    /**
     * 문서 상세 조회
     */
    @Transactional(readOnly = true)
    public KbDocumentResponse getDetail(UUID docId) {
        KnowledgeDocumentJpaEntity entity = kbDocRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));
        return toResponse(entity);
    }

    /**
     * 개별 문서 인덱싱 (비동기)
     */
    @Transactional
    public KbIndexingResponse indexOne(UUID docId) {
        KnowledgeDocumentJpaEntity doc = kbDocRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        // 이미 인덱싱 진행 중이면 409 Conflict
        if ("INDEXING".equals(doc.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 인덱싱이 진행 중입니다.");
        }

        // 상태를 INDEXING으로 변경하고 저장
        doc.markIndexing();
        kbDocRepository.save(doc);

        // 트랜잭션 커밋 후 비동기 워커 실행 (커밋 전 호출하면 DB 미반영 상태에서 워커가 시작됨)
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                indexingWorker.indexOneAsync(docId);
                log.info("kb.indexing.queued documentId={}", docId);
            }
        });

        return new KbIndexingResponse(docId, doc.getStatus(), 0, 0);
    }

    /**
     * 미인덱싱 문서 일괄 인덱싱 (비동기)
     */
    @Transactional
    public KbBatchIndexingResponse indexAll() {
        List<KnowledgeDocumentJpaEntity> docs = kbDocRepository.findByStatusIn(List.of("UPLOADED", "FAILED"));

        List<UUID> queuedIds = new java.util.ArrayList<>();
        for (KnowledgeDocumentJpaEntity doc : docs) {
            try {
                if ("INDEXING".equals(doc.getStatus())) {
                    continue;
                }
                doc.markIndexing();
                kbDocRepository.save(doc);
                queuedIds.add(doc.getId());
            } catch (Exception e) {
                log.warn("kb.batchIndexing.queueFailed documentId={} error={}", doc.getId(), e.getMessage());
            }
        }

        // 트랜잭션 커밋 후 비동기 워커 일괄 실행
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (UUID id : queuedIds) {
                    indexingWorker.indexOneAsync(id);
                }
                log.info("kb.batchIndexing.queued count={}", queuedIds.size());
            }
        });

        return new KbBatchIndexingResponse(queuedIds.size(), 0, 0);
    }

    /**
     * 문서 삭제
     */
    @Transactional
    public void delete(UUID docId) {
        KnowledgeDocumentJpaEntity doc = kbDocRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        // 1. 청크 삭제
        chunkRepository.deleteByDocumentId(docId);

        // 2. 벡터 삭제
        vectorStore.deleteByDocumentId(docId);

        // 3. 파일 삭제
        try {
            Files.deleteIfExists(Path.of(doc.getStoragePath()));
        } catch (IOException e) {
            log.warn("kb.delete.file.failed documentId={} path={}", docId, doc.getStoragePath());
        }

        // 4. 엔티티 삭제
        kbDocRepository.delete(doc);

        log.info("kb.delete.success documentId={}", docId);
    }

    /**
     * 통계 조회
     */
    @Transactional(readOnly = true)
    public KbStatsResponse getStats() {
        long totalDocuments = kbDocRepository.count();
        long indexedDocuments = kbDocRepository.countByStatus("INDEXED");

        // 청크 수 (KNOWLEDGE_BASE 타입만)
        long totalChunks = chunkRepository.countBySourceType("KNOWLEDGE_BASE");

        // 카테고리별 집계
        List<KnowledgeDocumentJpaEntity> allDocs = kbDocRepository.findAll();
        Map<String, Long> byCategory = allDocs.stream()
                .collect(Collectors.groupingBy(KnowledgeDocumentJpaEntity::getCategory, Collectors.counting()));

        Map<String, Long> byProductFamily = allDocs.stream()
                .filter(d -> d.getProductFamily() != null && !d.getProductFamily().isBlank())
                .collect(Collectors.groupingBy(KnowledgeDocumentJpaEntity::getProductFamily, Collectors.counting()));

        return new KbStatsResponse(totalDocuments, indexedDocuments, totalChunks, byCategory, byProductFamily);
    }

    /**
     * 기존 문서에 대해 AI 메타데이터 분석을 실행한다.
     */
    @Transactional
    public KbDocumentResponse analyzeMetadata(UUID docId) {
        KnowledgeDocumentJpaEntity entity = kbDocRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        enrichWithAi(entity, Path.of(entity.getStoragePath()), entity.getContentType());
        return toResponse(entity);
    }

    // ===== AI 메타데이터 자동 채움 =====

    private void enrichWithAi(KnowledgeDocumentJpaEntity entity, Path filePath, String contentType) {
        boolean needsEnrichment =
                (entity.getProductFamily() == null || entity.getProductFamily().isBlank())
                || (entity.getDescription() == null || entity.getDescription().isBlank())
                || (entity.getTags() == null || entity.getTags().isBlank());

        if (!needsEnrichment || !metadataAnalyzer.isAvailable()) {
            return;
        }

        try {
            DocumentMetadataAnalyzer.MetadataSuggestion suggestion =
                    metadataAnalyzer.analyze(filePath, contentType);

            if (suggestion != null) {
                entity.enrichMetadata(
                        suggestion.category(),
                        suggestion.productFamily(),
                        suggestion.description(),
                        suggestion.tags()
                );
                kbDocRepository.save(entity);
                log.info("kb.ai.enrich.success documentId={} category={} productFamily={}",
                        entity.getId(), entity.getCategory(), entity.getProductFamily());
            }
        } catch (Exception e) {
            log.warn("kb.ai.enrich.failed documentId={} error={}", entity.getId(), e.getMessage());
            // AI 실패해도 업로드 자체는 성공으로 처리
        }
    }

    // ===== 헬퍼 메서드 =====

    private KbDocumentResponse toResponse(KnowledgeDocumentJpaEntity entity) {
        return new KbDocumentResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getCategory(),
                entity.getProductFamily(),
                entity.getFileName(),
                entity.getFileSize(),
                entity.getStatus(),
                entity.getChunkCount(),
                entity.getVectorCount(),
                entity.getUploadedBy(),
                entity.getTags(),
                entity.getDescription(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
