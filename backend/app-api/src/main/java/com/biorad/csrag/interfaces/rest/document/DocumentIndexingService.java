package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
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

    private final DocumentMetadataJpaRepository documentRepository;

    public DocumentIndexingService(DocumentMetadataJpaRepository documentRepository) {
        this.documentRepository = documentRepository;
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
                doc.markParsed(extracted);
                succeeded++;
            } catch (Exception e) {
                doc.markFailed(e.getMessage());
                failed++;
            }
        }

        return new IndexingRunResponse(inquiryId.toString(), processed, succeeded, failed);
    }

    private String extractText(DocumentMetadataJpaEntity document) throws IOException {
        byte[] bytes = Files.readAllBytes(Path.of(document.getStoragePath()));
        String raw = new String(bytes, StandardCharsets.UTF_8);
        String normalized = raw.replaceAll("\\p{Cntrl}", " ").trim();

        if (normalized.isBlank()) {
            throw new IllegalStateException("no_extractable_text");
        }

        if (normalized.length() > 10000) {
            return normalized.substring(0, 10000);
        }
        return normalized;
    }
}
