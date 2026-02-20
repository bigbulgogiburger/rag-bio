package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ChunkingService;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Inquiry document async indexing worker.
 * Separated from DocumentIndexingService so that @Async proxy works correctly.
 */
@Component
public class DocumentIndexingWorker {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingWorker.class);
    private static final int MAX_TEXT_LENGTH = 10_000;

    private final DocumentMetadataJpaRepository documentRepository;
    private final DocumentTextExtractor textExtractor;
    private final OcrService ocrService;
    private final ChunkingService chunkingService;
    private final VectorizingService vectorizingService;
    private final SseService sseService;

    public DocumentIndexingWorker(
            DocumentMetadataJpaRepository documentRepository,
            DocumentTextExtractor textExtractor,
            OcrService ocrService,
            ChunkingService chunkingService,
            VectorizingService vectorizingService,
            SseService sseService
    ) {
        this.documentRepository = documentRepository;
        this.textExtractor = textExtractor;
        this.ocrService = ocrService;
        this.chunkingService = chunkingService;
        this.vectorizingService = vectorizingService;
        this.sseService = sseService;
    }

    /**
     * Index a single document asynchronously.
     * Uses REQUIRES_NEW to run in an independent transaction.
     */
    @Async("docIndexingExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void indexOneAsync(UUID documentId) {
        DocumentMetadataJpaEntity doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("document.async-indexing.skip documentId={} reason=not_found", documentId);
            return;
        }

        // Guard: only index UPLOADED or FAILED_PARSING documents
        if (!("UPLOADED".equals(doc.getStatus()) || "FAILED_PARSING".equals(doc.getStatus()))) {
            log.info("document.async-indexing.skip documentId={} status={} reason=not_eligible", documentId, doc.getStatus());
            return;
        }

        UUID inquiryId = doc.getInquiryId();

        try {
            doc.markParsing();
            documentRepository.save(doc);
            emitIndexingEvent(inquiryId, documentId, "PARSING", 10, null);

            String extracted = extractText(doc);

            String finalText;
            int chunkCount;
            if (needsOcr(doc, extracted)) {
                OcrResult ocr = ocrService.extract(Path.of(doc.getStoragePath()));
                finalText = limitText(ocr.text());
                doc.markParsedFromOcr(finalText, ocr.confidence());
                documentRepository.save(doc);
                log.info("document.async-indexing.ocr.success documentId={} confidence={}", doc.getId(), ocr.confidence());
                emitIndexingEvent(inquiryId, documentId, "PARSED_OCR", 40, null);
                chunkCount = chunkingService.chunkAndStore(doc.getId(), finalText);
            } else {
                List<DocumentTextExtractor.PageText> pageTexts =
                        textExtractor.extractByPage(Path.of(doc.getStoragePath()), doc.getContentType());
                finalText = limitText(pageTexts.stream()
                        .map(DocumentTextExtractor.PageText::text)
                        .collect(Collectors.joining(" ")));
                doc.markParsed(finalText);
                documentRepository.save(doc);
                emitIndexingEvent(inquiryId, documentId, "PARSED", 40, null);
                chunkCount = chunkingService.chunkAndStore(doc.getId(), pageTexts, "INQUIRY", doc.getId());
            }

            doc.markChunked(chunkCount);
            documentRepository.save(doc);
            log.info("document.async-indexing.chunking.success documentId={} chunkCount={}", doc.getId(), chunkCount);
            emitIndexingEvent(inquiryId, documentId, "CHUNKED", 70, null);

            int vectorCount = vectorizingService.upsertDocumentChunks(doc.getId());
            doc.markIndexed(vectorCount);
            documentRepository.save(doc);
            log.info("document.async-indexing.success documentId={} chunkCount={} vectorCount={}", doc.getId(), chunkCount, vectorCount);
            emitIndexingEvent(inquiryId, documentId, "INDEXED", 100, null);

        } catch (Exception e) {
            doc.markFailed(e.getMessage());
            documentRepository.save(doc);
            log.warn("document.async-indexing.failed documentId={} error={}", doc.getId(), e.getMessage());
            emitIndexingEvent(inquiryId, documentId, "FAILED", 0, e.getMessage());
        }
    }

    private void emitIndexingEvent(UUID inquiryId, UUID documentId, String status, int progress, String error) {
        try {
            sseService.send(inquiryId, "indexing-progress", Map.of(
                    "documentId", documentId.toString(),
                    "status", status,
                    "progress", progress,
                    "error", error == null ? "" : error
            ));
        } catch (Exception e) {
            log.debug("sse.emit.failed inquiryId={} documentId={} event=indexing-progress", inquiryId, documentId);
        }
    }

    private String extractText(DocumentMetadataJpaEntity document) throws IOException {
        return textExtractor.extract(
                Path.of(document.getStoragePath()),
                document.getContentType()
        );
    }

    private boolean needsOcr(DocumentMetadataJpaEntity document, String extracted) {
        boolean likelyPdf = "application/pdf".equalsIgnoreCase(document.getContentType());
        return likelyPdf && extracted.length() < 20;
    }

    private String limitText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("no_extractable_text");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            return text.substring(0, MAX_TEXT_LENGTH);
        }
        return text;
    }
}
