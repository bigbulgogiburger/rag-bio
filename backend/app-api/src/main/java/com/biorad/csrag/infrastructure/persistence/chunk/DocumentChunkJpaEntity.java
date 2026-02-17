package com.biorad.csrag.infrastructure.persistence.chunk;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkJpaEntity {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "start_offset", nullable = false)
    private int startOffset;

    @Column(name = "end_offset", nullable = false)
    private int endOffset;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "source_type", length = 20)
    private String sourceType = "INQUIRY";    // INQUIRY 또는 KNOWLEDGE_BASE

    @Column(name = "source_id")
    private UUID sourceId;                     // 원본 문서 ID

    @Column(name = "page_start")
    private Integer pageStart;                 // PDF 시작 페이지 (1-based, nullable)

    @Column(name = "page_end")
    private Integer pageEnd;                   // PDF 끝 페이지 (1-based, nullable)

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DocumentChunkJpaEntity() {
    }

    public DocumentChunkJpaEntity(UUID id, UUID documentId, int chunkIndex, int startOffset, int endOffset, String content, Instant createdAt) {
        this(id, documentId, chunkIndex, startOffset, endOffset, content, "INQUIRY", documentId, createdAt);
    }

    public DocumentChunkJpaEntity(UUID id, UUID documentId, int chunkIndex, int startOffset, int endOffset, String content, String sourceType, UUID sourceId, Instant createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.startOffset = startOffset;
        this.endOffset = endOffset;
        this.content = content;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.createdAt = createdAt;
    }

    public DocumentChunkJpaEntity(UUID id, UUID documentId, int chunkIndex, int startOffset, int endOffset, String content, String sourceType, UUID sourceId, Integer pageStart, Integer pageEnd, Instant createdAt) {
        this(id, documentId, chunkIndex, startOffset, endOffset, content, sourceType, sourceId, createdAt);
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getStartOffset() {
        return startOffset;
    }

    public int getEndOffset() {
        return endOffset;
    }

    public String getContent() {
        return content;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public Integer getPageStart() {
        return pageStart;
    }

    public Integer getPageEnd() {
        return pageEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
