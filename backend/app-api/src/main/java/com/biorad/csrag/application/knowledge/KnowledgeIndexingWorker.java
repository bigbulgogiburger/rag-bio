package com.biorad.csrag.application.knowledge;

import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ChunkingService;
import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Knowledge Base 문서 비동기 인덱싱 워커
 * @Async 메서드는 Spring AOP 프록시 제약으로 인해 별도 클래스로 분리
 */
@Component
public class KnowledgeIndexingWorker {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexingWorker.class);
    private static final int MAX_TEXT_LENGTH = 500_000;

    private final KnowledgeDocumentJpaRepository kbDocRepository;
    private final ChunkingService chunkingService;
    private final VectorizingService vectorizingService;
    private final OcrService ocrService;
    private final DocumentTextExtractor textExtractor;

    public KnowledgeIndexingWorker(
            KnowledgeDocumentJpaRepository kbDocRepository,
            ChunkingService chunkingService,
            VectorizingService vectorizingService,
            OcrService ocrService,
            DocumentTextExtractor textExtractor
    ) {
        this.kbDocRepository = kbDocRepository;
        this.chunkingService = chunkingService;
        this.vectorizingService = vectorizingService;
        this.ocrService = ocrService;
        this.textExtractor = textExtractor;
    }

    /**
     * 개별 문서를 비동기로 인덱싱한다.
     * REQUIRES_NEW: 호출자의 트랜잭션과 독립적으로 실행
     */
    @Async("kbIndexingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void indexOneAsync(UUID docId) {
        KnowledgeDocumentJpaEntity doc = kbDocRepository.findById(docId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found"));

        try {
            doc.markParsing();
            kbDocRepository.save(doc);

            // 텍스트 추출 (PDF: PDFBox, DOCX: POI)
            String extracted = extractText(doc.getStoragePath(), doc.getContentType());

            // OCR 필요 시 처리
            String finalText;
            int chunkCount;
            if (needsOcr(extracted)) {
                OcrResult ocr = ocrService.extract(Path.of(doc.getStoragePath()));
                finalText = limitText(ocr.text());
                doc.markParsedFromOcr(finalText, ocr.confidence());
                kbDocRepository.save(doc);
                chunkCount = chunkingService.chunkAndStore(doc.getId(), finalText, "KNOWLEDGE_BASE", doc.getId());
            } else {
                // 페이지별 추출 사용 (PDF는 페이지 정보 보존)
                List<DocumentTextExtractor.PageText> pageTexts =
                        textExtractor.extractByPage(Path.of(doc.getStoragePath()), doc.getContentType());
                finalText = limitText(pageTexts.stream()
                        .map(DocumentTextExtractor.PageText::text)
                        .collect(Collectors.joining(" ")));
                doc.markParsed(finalText);
                kbDocRepository.save(doc);
                chunkCount = chunkingService.chunkAndStore(doc.getId(), pageTexts, "KNOWLEDGE_BASE", doc.getId());
            }
            doc.markChunked(chunkCount);
            kbDocRepository.save(doc);

            // 벡터화
            int vectorCount = vectorizingService.upsertDocumentChunks(doc.getId());
            doc.markIndexed(vectorCount);
            kbDocRepository.save(doc);

            log.info("kb.indexing.success documentId={} chunkCount={} vectorCount={}", docId, chunkCount, vectorCount);

        } catch (Exception e) {
            doc.markFailed(e.getMessage());
            kbDocRepository.save(doc);
            log.warn("kb.indexing.failed documentId={} error={}", docId, e.getMessage());
        }
    }

    // ===== 헬퍼 메서드 =====

    private String extractText(String storagePath, String contentType) throws IOException {
        return textExtractor.extract(Path.of(storagePath), contentType);
    }

    private boolean needsOcr(String extracted) {
        return extracted == null || extracted.isBlank() || extracted.length() < 50;
    }

    private String limitText(String text) {
        if (text == null) return "";
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }
}
