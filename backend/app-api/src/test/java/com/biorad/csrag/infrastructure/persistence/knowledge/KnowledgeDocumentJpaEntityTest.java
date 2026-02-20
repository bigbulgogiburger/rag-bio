package com.biorad.csrag.infrastructure.persistence.knowledge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeDocumentJpaEntityTest {

    private KnowledgeDocumentJpaEntity createEntity() {
        return KnowledgeDocumentJpaEntity.create(
                "Test Manual",
                "MANUAL",
                "CFX96",
                "test.pdf",
                "application/pdf",
                1024,
                "/storage/test.pdf",
                "Test description",
                "pcr,manual",
                "admin"
        );
    }

    @Test
    void create_setsAllFields() {
        var entity = createEntity();
        assertThat(entity.getId()).isNotNull();
        assertThat(entity.getTitle()).isEqualTo("Test Manual");
        assertThat(entity.getCategory()).isEqualTo("MANUAL");
        assertThat(entity.getProductFamily()).isEqualTo("CFX96");
        assertThat(entity.getFileName()).isEqualTo("test.pdf");
        assertThat(entity.getContentType()).isEqualTo("application/pdf");
        assertThat(entity.getFileSize()).isEqualTo(1024);
        assertThat(entity.getStoragePath()).isEqualTo("/storage/test.pdf");
        assertThat(entity.getStatus()).isEqualTo("UPLOADED");
        assertThat(entity.getDescription()).isEqualTo("Test description");
        assertThat(entity.getTags()).isEqualTo("pcr,manual");
        assertThat(entity.getUploadedBy()).isEqualTo("admin");
        assertThat(entity.getCreatedAt()).isNotNull();
        assertThat(entity.getUpdatedAt()).isNotNull();
    }

    @Test
    void markIndexing_updatesStatus() {
        var entity = createEntity();
        entity.markIndexing();
        assertThat(entity.getStatus()).isEqualTo("INDEXING");
    }

    @Test
    void markParsing_updatesStatus() {
        var entity = createEntity();
        entity.markParsing();
        assertThat(entity.getStatus()).isEqualTo("PARSING");
    }

    @Test
    void markParsed_updatesStatusAndText() {
        var entity = createEntity();
        entity.markParsed("extracted text");
        assertThat(entity.getStatus()).isEqualTo("PARSED");
        assertThat(entity.getExtractedText()).isEqualTo("extracted text");
    }

    @Test
    void markParsedFromOcr_updatesStatusTextAndConfidence() {
        var entity = createEntity();
        entity.markParsedFromOcr("ocr text", 0.95);
        assertThat(entity.getStatus()).isEqualTo("PARSED_OCR");
        assertThat(entity.getExtractedText()).isEqualTo("ocr text");
        assertThat(entity.getOcrConfidence()).isEqualTo(0.95);
    }

    @Test
    void markChunked_updatesStatusAndCount() {
        var entity = createEntity();
        entity.markChunked(10);
        assertThat(entity.getStatus()).isEqualTo("CHUNKED");
        assertThat(entity.getChunkCount()).isEqualTo(10);
    }

    @Test
    void markIndexed_updatesStatusAndVectorCount() {
        var entity = createEntity();
        entity.markIndexed(5);
        assertThat(entity.getStatus()).isEqualTo("INDEXED");
        assertThat(entity.getVectorCount()).isEqualTo(5);
    }

    @Test
    void markFailed_updatesStatusAndError() {
        var entity = createEntity();
        entity.markFailed("Timeout during parsing");
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getLastError()).isEqualTo("Timeout during parsing");
    }

    @Test
    void enrichMetadata_fillsEmptyFields() {
        var entity = KnowledgeDocumentJpaEntity.create(
                "Test", "MANUAL", null, "test.pdf", "application/pdf",
                1024, "/storage/test.pdf", null, null, null
        );
        entity.enrichMetadata("PROTOCOL", "QX200", "Auto description", "droplet,digital-pcr");

        assertThat(entity.getCategory()).isEqualTo("PROTOCOL");
        assertThat(entity.getProductFamily()).isEqualTo("QX200");
        assertThat(entity.getDescription()).isEqualTo("Auto description");
        assertThat(entity.getTags()).isEqualTo("droplet,digital-pcr");
    }

    @Test
    void enrichMetadata_doesNotOverwriteExistingValues() {
        var entity = createEntity();
        entity.enrichMetadata("PROTOCOL", "QX200", "New description", "new,tags");

        assertThat(entity.getCategory()).isEqualTo("PROTOCOL"); // MANUAL gets overwritten (special case)
        assertThat(entity.getProductFamily()).isEqualTo("CFX96"); // NOT overwritten
        assertThat(entity.getDescription()).isEqualTo("Test description"); // NOT overwritten
        assertThat(entity.getTags()).isEqualTo("pcr,manual"); // NOT overwritten
    }

    @Test
    void enrichMetadata_nullSuggestions_noChange() {
        var entity = createEntity();
        String originalCategory = entity.getCategory();
        entity.enrichMetadata(null, null, null, null);
        assertThat(entity.getCategory()).isEqualTo(originalCategory);
    }

    @Test
    void enrichMetadata_blankSuggestions_noChange() {
        var entity = KnowledgeDocumentJpaEntity.create(
                "Test", "PROTOCOL", "CFX96", "test.pdf", "application/pdf",
                1024, "/storage/test.pdf", "desc", "tags", "admin"
        );
        entity.enrichMetadata("", "", "", "");
        assertThat(entity.getCategory()).isEqualTo("PROTOCOL");
        assertThat(entity.getProductFamily()).isEqualTo("CFX96");
    }

    @Test
    void fullLifecycle_uploadToIndexed() {
        var entity = createEntity();
        assertThat(entity.getStatus()).isEqualTo("UPLOADED");

        entity.markParsing();
        assertThat(entity.getStatus()).isEqualTo("PARSING");

        entity.markParsed("text");
        assertThat(entity.getStatus()).isEqualTo("PARSED");

        entity.markChunked(5);
        assertThat(entity.getStatus()).isEqualTo("CHUNKED");

        entity.markIndexed(5);
        assertThat(entity.getStatus()).isEqualTo("INDEXED");
    }

    @Test
    void fullLifecycle_uploadToFailed() {
        var entity = createEntity();
        entity.markParsing();
        entity.markFailed("Parse error");
        assertThat(entity.getStatus()).isEqualTo("FAILED");
        assertThat(entity.getLastError()).isEqualTo("Parse error");
    }

    @Test
    void create_nullOptionalFields() {
        var entity = KnowledgeDocumentJpaEntity.create(
                "Title", "FAQ", null, "faq.pdf", "application/pdf",
                512, "/path", null, null, null
        );
        assertThat(entity.getProductFamily()).isNull();
        assertThat(entity.getDescription()).isNull();
        assertThat(entity.getTags()).isNull();
        assertThat(entity.getUploadedBy()).isNull();
        assertThat(entity.getExtractedText()).isNull();
        assertThat(entity.getOcrConfidence()).isNull();
        assertThat(entity.getChunkCount()).isNull();
        assertThat(entity.getVectorCount()).isNull();
        assertThat(entity.getLastError()).isNull();
    }
}
