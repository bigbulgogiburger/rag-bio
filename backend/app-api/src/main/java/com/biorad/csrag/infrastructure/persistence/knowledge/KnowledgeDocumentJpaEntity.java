package com.biorad.csrag.infrastructure.persistence.knowledge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Knowledge Base 문서 엔티티
 * 문의와 독립적으로 관리되는 기술 문서(매뉴얼, 프로토콜, FAQ, 스펙시트)
 */
@Entity
@Table(name = "knowledge_documents")
public class KnowledgeDocumentJpaEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 100)
    private String category;       // MANUAL, PROTOCOL, FAQ, SPEC_SHEET

    @Column(name = "product_family", length = 200)
    private String productFamily;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false, length = 40)
    private String status;         // UPLOADED, PARSING, PARSED, CHUNKED, INDEXED, FAILED

    @Column(length = 2000)
    private String description;

    @Column(length = 500)
    private String tags;

    @Column(name = "uploaded_by", length = 120)
    private String uploadedBy;

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "vector_count")
    private Integer vectorCount;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected KnowledgeDocumentJpaEntity() {
    }

    private KnowledgeDocumentJpaEntity(
            UUID id,
            String title,
            String category,
            String productFamily,
            String fileName,
            String contentType,
            long fileSize,
            String storagePath,
            String description,
            String tags,
            String uploadedBy
    ) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.productFamily = productFamily;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.status = "UPLOADED";
        this.description = description;
        this.tags = tags;
        this.uploadedBy = uploadedBy;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 신규 Knowledge Base 문서 생성 팩토리 메서드
     */
    public static KnowledgeDocumentJpaEntity create(
            String title,
            String category,
            String productFamily,
            String fileName,
            String contentType,
            long fileSize,
            String storagePath,
            String description,
            String tags,
            String uploadedBy
    ) {
        return new KnowledgeDocumentJpaEntity(
                UUID.randomUUID(),
                title,
                category,
                productFamily,
                fileName,
                contentType,
                fileSize,
                storagePath,
                description,
                tags,
                uploadedBy
        );
    }

    // ===== 상태 전환 메서드 =====

    public void markParsing() {
        this.status = "PARSING";
        this.updatedAt = Instant.now();
    }

    public void markParsed(String text) {
        this.status = "PARSED";
        this.extractedText = text;
        this.updatedAt = Instant.now();
    }

    public void markParsedFromOcr(String text, double confidence) {
        this.status = "PARSED_OCR";
        this.extractedText = text;
        this.ocrConfidence = confidence;
        this.updatedAt = Instant.now();
    }

    public void markChunked(int count) {
        this.status = "CHUNKED";
        this.chunkCount = count;
        this.updatedAt = Instant.now();
    }

    public void markIndexed(int count) {
        this.status = "INDEXED";
        this.vectorCount = count;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.lastError = error;
        this.updatedAt = Instant.now();
    }

    // ===== Getters =====

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getProductFamily() {
        return productFamily;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getTags() {
        return tags;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public String getExtractedText() {
        return extractedText;
    }

    public Double getOcrConfidence() {
        return ocrConfidence;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public Integer getVectorCount() {
        return vectorCount;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
