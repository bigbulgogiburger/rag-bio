package com.biorad.csrag.infrastructure.persistence.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentMetadataJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "status", nullable = false, length = 40)
    private String status;

    @Lob
    @Column(name = "extracted_text")
    private String extractedText;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentMetadataJpaEntity() {
    }

    public DocumentMetadataJpaEntity(
            UUID id,
            UUID inquiryId,
            String fileName,
            String contentType,
            long fileSize,
            String storagePath,
            String status,
            String extractedText,
            String lastError,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storagePath = storagePath;
        this.status = status;
        this.extractedText = extractedText;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getStoragePath() { return storagePath; }
    public String getStatus() { return status; }
    public String getExtractedText() { return extractedText; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markParsing() {
        this.status = "PARSING";
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markParsed(String extractedText) {
        this.status = "PARSED";
        this.extractedText = extractedText;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED_PARSING";
        this.lastError = error == null ? "unknown" : error;
        this.updatedAt = Instant.now();
    }
}
