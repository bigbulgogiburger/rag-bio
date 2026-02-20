package com.biorad.csrag.infrastructure.persistence.document;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMetadataJpaEntityTest {

    @Test
    void markParsing_setsStatusAndClearsError() {
        DocumentMetadataJpaEntity doc = createDocument("UPLOADED");
        doc.markParsing();

        assertThat(doc.getStatus()).isEqualTo("PARSING");
        assertThat(doc.getLastError()).isNull();
    }

    @Test
    void markParsed_setsExtractedText() {
        DocumentMetadataJpaEntity doc = createDocument("PARSING");
        doc.markParsed("Extracted content here");

        assertThat(doc.getStatus()).isEqualTo("PARSED");
        assertThat(doc.getExtractedText()).isEqualTo("Extracted content here");
        assertThat(doc.getOcrConfidence()).isNull();
    }

    @Test
    void markParsedFromOcr_setsOcrConfidence() {
        DocumentMetadataJpaEntity doc = createDocument("PARSING");
        doc.markParsedFromOcr("OCR text", 0.92);

        assertThat(doc.getStatus()).isEqualTo("PARSED_OCR");
        assertThat(doc.getOcrConfidence()).isEqualTo(0.92);
    }

    @Test
    void markChunked_setsChunkCount() {
        DocumentMetadataJpaEntity doc = createDocument("PARSED");
        doc.markChunked(15);

        assertThat(doc.getStatus()).isEqualTo("CHUNKED");
        assertThat(doc.getChunkCount()).isEqualTo(15);
        assertThat(doc.getVectorCount()).isNull();
    }

    @Test
    void markIndexed_setsVectorCount() {
        DocumentMetadataJpaEntity doc = createDocument("CHUNKED");
        doc.markIndexed(15);

        assertThat(doc.getStatus()).isEqualTo("INDEXED");
        assertThat(doc.getVectorCount()).isEqualTo(15);
    }

    @Test
    void markFailed_setsErrorMessage() {
        DocumentMetadataJpaEntity doc = createDocument("PARSING");
        doc.markFailed("PDF parsing error");

        assertThat(doc.getStatus()).isEqualTo("FAILED_PARSING");
        assertThat(doc.getLastError()).isEqualTo("PDF parsing error");
    }

    @Test
    void markFailed_nullError_setsUnknown() {
        DocumentMetadataJpaEntity doc = createDocument("PARSING");
        doc.markFailed(null);

        assertThat(doc.getLastError()).isEqualTo("unknown");
    }

    @Test
    void statusTransitions_fullPipeline() {
        DocumentMetadataJpaEntity doc = createDocument("UPLOADED");

        doc.markParsing();
        assertThat(doc.getStatus()).isEqualTo("PARSING");

        doc.markParsed("text");
        assertThat(doc.getStatus()).isEqualTo("PARSED");

        doc.markChunked(10);
        assertThat(doc.getStatus()).isEqualTo("CHUNKED");

        doc.markIndexed(10);
        assertThat(doc.getStatus()).isEqualTo("INDEXED");
    }

    private DocumentMetadataJpaEntity createDocument(String status) {
        return new DocumentMetadataJpaEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "test.pdf",
                "application/pdf",
                1024L,
                "/uploads/test.pdf",
                status,
                null, null, null, null, null,
                Instant.now(), Instant.now()
        );
    }
}
