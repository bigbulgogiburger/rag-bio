package com.biorad.csrag.infrastructure.persistence.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
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

    @Column(name = "extracted_text", columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "ocr_confidence")
    private Double ocrConfidence;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "vector_count")
    private Integer vectorCount;

    @Column(name = "image_analysis_type", length = 20)
    private String imageAnalysisType;

    @Column(name = "image_extracted_text", columnDefinition = "TEXT")
    private String imageExtractedText;

    @Column(name = "image_visual_description", columnDefinition = "TEXT")
    private String imageVisualDescription;

    @Column(name = "image_technical_context", columnDefinition = "TEXT")
    private String imageTechnicalContext;

    @Column(name = "image_suggested_query", columnDefinition = "TEXT")
    private String imageSuggestedQuery;

    @Column(name = "image_analysis_confidence")
    private Double imageAnalysisConfidence;

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
            Double ocrConfidence,
            Integer chunkCount,
            Integer vectorCount,
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
        this.ocrConfidence = ocrConfidence;
        this.chunkCount = chunkCount;
        this.vectorCount = vectorCount;
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
    public Double getOcrConfidence() { return ocrConfidence; }
    public Integer getChunkCount() { return chunkCount; }
    public Integer getVectorCount() { return vectorCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public String getImageAnalysisType() { return imageAnalysisType; }
    public String getImageExtractedText() { return imageExtractedText; }
    public String getImageVisualDescription() { return imageVisualDescription; }
    public String getImageTechnicalContext() { return imageTechnicalContext; }
    public String getImageSuggestedQuery() { return imageSuggestedQuery; }
    public Double getImageAnalysisConfidence() { return imageAnalysisConfidence; }

    public void setImageAnalysis(
            String imageType,
            String extractedText,
            String visualDescription,
            String technicalContext,
            String suggestedQuery,
            double confidence
    ) {
        this.imageAnalysisType = imageType;
        this.imageExtractedText = extractedText;
        this.imageVisualDescription = visualDescription;
        this.imageTechnicalContext = technicalContext;
        this.imageSuggestedQuery = suggestedQuery;
        this.imageAnalysisConfidence = confidence;
        this.updatedAt = Instant.now();
    }

    public void markParsing() {
        this.status = "PARSING";
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markParsed(String extractedText) {
        this.status = "PARSED";
        this.extractedText = extractedText;
        this.lastError = null;
        this.ocrConfidence = null;
        this.chunkCount = null;
        this.vectorCount = null;
        this.updatedAt = Instant.now();
    }

    public void markParsedFromOcr(String extractedText, double confidence) {
        this.status = "PARSED_OCR";
        this.extractedText = extractedText;
        this.lastError = null;
        this.ocrConfidence = confidence;
        this.chunkCount = null;
        this.vectorCount = null;
        this.updatedAt = Instant.now();
    }

    public void markChunked(int chunkCount) {
        this.status = "CHUNKED";
        this.chunkCount = chunkCount;
        this.vectorCount = null;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markIndexed(int vectorCount) {
        this.status = "INDEXED";
        this.vectorCount = vectorCount;
        this.lastError = null;
        this.updatedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED_PARSING";
        this.lastError = error == null ? "unknown" : error;
        this.chunkCount = null;
        this.vectorCount = null;
        this.updatedAt = Instant.now();
    }
}
