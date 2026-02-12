package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
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

    public DocumentIndexingService(DocumentMetadataJpaRepository documentRepository, OcrService ocrService) {
        this.documentRepository = documentRepository;
        this.ocrService = ocrService;
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

                if (needsOcr(doc, extracted)) {
                    OcrResult ocr = ocrService.extract(Path.of(doc.getStoragePath()));
                    doc.markParsedFromOcr(limitText(ocr.text()), ocr.confidence());
                    log.info("document.indexing.ocr.success documentId={} confidence={}", doc.getId(), ocr.confidence());
                } else {
                    doc.markParsed(limitText(extracted));
                }

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
