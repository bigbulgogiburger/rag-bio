package com.biorad.csrag.application.knowledge;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeBaseSpecifications;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ChunkingService;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
import com.biorad.csrag.interfaces.rest.dto.knowledge.*;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    private static final int MAX_TEXT_LENGTH = 500_000;

    private final KnowledgeDocumentJpaRepository kbDocRepository;
    private final DocumentChunkJpaRepository chunkRepository;
    private final ChunkingService chunkingService;
    private final VectorizingService vectorizingService;
    private final VectorStore vectorStore;
    private final OcrService ocrService;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public KnowledgeBaseService(
            KnowledgeDocumentJpaRepository kbDocRepository,
            DocumentChunkJpaRepository chunkRepository,
            ChunkingService chunkingService,
            VectorizingService vectorizingService,
            VectorStore vectorStore,
            OcrService ocrService
    ) {
        this.kbDocRepository = kbDocRepository;
        this.chunkRepository = chunkRepository;
        this.chunkingService = chunkingService;
        this.vectorizingService = vectorizingService;
        this.vectorStore = vectorStore;
        this.ocrService = ocrService;
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
     * 개별 문서 인덱싱
     */
    @Transactional
    public KbIndexingResponse indexOne(UUID docId) {
        KnowledgeDocumentJpaEntity doc = kbDocRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        try {
            doc.markParsing();

            // 텍스트 추출
            String extracted = extractText(doc.getStoragePath());

            // OCR 필요 시 처리
            String finalText;
            if (needsOcr(extracted)) {
                OcrResult ocr = ocrService.extract(Path.of(doc.getStoragePath()));
                finalText = limitText(ocr.text());
                doc.markParsedFromOcr(finalText, ocr.confidence());
            } else {
                finalText = limitText(extracted);
                doc.markParsed(finalText);
            }

            // 청킹 (KNOWLEDGE_BASE 타입으로)
            int chunkCount = chunkingService.chunkAndStore(doc.getId(), finalText, "KNOWLEDGE_BASE", doc.getId());
            doc.markChunked(chunkCount);

            // 벡터화
            int vectorCount = vectorizingService.upsertDocumentChunks(doc.getId());
            doc.markIndexed(vectorCount);

            log.info("kb.indexing.success documentId={} chunkCount={} vectorCount={}", docId, chunkCount, vectorCount);
            return new KbIndexingResponse(docId, doc.getStatus(), chunkCount, vectorCount);

        } catch (Exception e) {
            doc.markFailed(e.getMessage());
            log.warn("kb.indexing.failed documentId={} error={}", docId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Indexing failed: " + e.getMessage());
        }
    }

    /**
     * 미인덱싱 문서 일괄 인덱싱
     */
    @Transactional
    public KbBatchIndexingResponse indexAll() {
        List<KnowledgeDocumentJpaEntity> docs = kbDocRepository.findByStatusIn(List.of("UPLOADED", "FAILED"));

        int processed = 0;
        int succeeded = 0;
        int failed = 0;

        for (KnowledgeDocumentJpaEntity doc : docs) {
            processed++;
            try {
                indexOne(doc.getId());
                succeeded++;
            } catch (Exception e) {
                failed++;
            }
        }

        log.info("kb.batchIndexing.complete processed={} succeeded={} failed={}", processed, succeeded, failed);
        return new KbBatchIndexingResponse(processed, succeeded, failed);
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

    private String extractText(String storagePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(storagePath));
        return new String(bytes, StandardCharsets.UTF_8)
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean needsOcr(String extracted) {
        return extracted == null || extracted.isBlank() || extracted.length() < 50;
    }

    private String limitText(String text) {
        if (text == null) return "";
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}
