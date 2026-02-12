package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ChunkingService;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentIndexingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexingService.class);

    private final DocumentMetadataJpaRepository documentRepository;
    private final OcrService ocrService;
    private final ChunkingService chunkingService;
    private final VectorizingService vectorizingService;

    public DocumentIndexingService(
            DocumentMetadataJpaRepository documentRepository,
            OcrService ocrService,
            ChunkingService chunkingService,
            VectorizingService vectorizingService
    ) {
        this.documentRepository = documentRepository;
        this.ocrService = ocrService;
        this.chunkingService = chunkingService;
        this.vectorizingService = vectorizingService;
    }

    @Transactional
    public IndexingRunResponse run(UUID inquiryId) {
        List<DocumentMetadataJpaEntity> docs = documentRepository.findByInquiryIdOrderByCreatedAtDesc(inquiryId);

        int processed = 0;
        int succeeded = 0;
        int failed = 0;

        for (DocumentMetadataJpaEntity doc : docs) {
            if (!("UPLOADED".equals(doc.getStatus()) || "FAILED_PARSING".equals(doc.getStatus()))) {
                continue;
            }

            processed++;
            try {
                doc.markParsing();
                String extracted = extractText(doc);

                String finalText;
                if (needsOcr(doc, extracted)) {
                    OcrResult ocr = ocrService.extract(Path.of(doc.getStoragePath()));
                    finalText = limitText(ocr.text());
                    doc.markParsedFromOcr(finalText, ocr.confidence());
                    log.info("document.indexing.ocr.success documentId={} confidence={}", doc.getId(), ocr.confidence());
                } else {
                    finalText = limitText(extracted);
                    doc.markParsed(finalText);
                }

                int chunkCount = chunkingService.chunkAndStore(doc.getId(), finalText);
                doc.markChunked(chunkCount);
                log.info("document.chunking.success documentId={} chunkCount={}", doc.getId(), chunkCount);

                int vectorCount = vectorizingService.upsertDocumentChunks(doc.getId());
                doc.markIndexed(vectorCount);
                log.info("document.vectorizing.success documentId={} vectorCount={}", doc.getId(), vectorCount);

                succeeded++;
            } catch (Exception e) {
                doc.markFailed(e.getMessage());
                failed++;
                log.warn("document.indexing.failed documentId={} error={}", doc.getId(), e.getMessage());
            }
        }

        return new IndexingRunResponse(inquiryId.toString(), processed, succeeded, failed);
    }

    private String extractText(DocumentMetadataJpaEntity document) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(document.getStoragePath()));
        return new String(bytes, StandardCharsets.UTF_8)
                .replaceAll("\\p{Cntrl}", " ")
                .trim();
    }

    private boolean needsOcr(DocumentMetadataJpaEntity document, String extracted) {
        boolean likelyPdf = "application/pdf".equalsIgnoreCase(document.getContentType());
        return likelyPdf && extracted.length() < 20;
    }

    private String limitText(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalStateException("no_extractable_text");
        }

        if (text.length() > 10000) {
            return text.substring(0, 10000);
        }
        return text;
    }
}
