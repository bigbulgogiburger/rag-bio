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

    @Column(name = "product_family", length = 100)
    private String productFamily;              // 제품 패밀리 (예: "naica", "vericheck", "QX700")

    @Column(name = "parent_chunk_id")
    private UUID parentChunkId;                // 부모 청크 ID (Child 청크만 설정)

    @Column(name = "chunk_level", length = 10)
    private String chunkLevel = "CHILD";       // "PARENT" | "CHILD"

    @Column(name = "context_prefix", columnDefinition = "TEXT")
    private String contextPrefix;              // LLM이 생성한 문맥 설명

    @Column(name = "enriched_content", columnDefinition = "TEXT")
    private String enrichedContent;            // contextPrefix + "\n" + content

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

    public String getProductFamily() {
        return productFamily;
    }

    public void setProductFamily(String productFamily) {
        this.productFamily = productFamily;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public UUID getParentChunkId() {
        return parentChunkId;
    }

    public void setParentChunkId(UUID parentChunkId) {
        this.parentChunkId = parentChunkId;
    }

    public String getChunkLevel() {
        return chunkLevel;
    }

    public void setChunkLevel(String chunkLevel) {
        this.chunkLevel = chunkLevel;
    }

    public String getContextPrefix() {
        return contextPrefix;
    }

    public void setContextPrefix(String contextPrefix) {
        this.contextPrefix = contextPrefix;
    }

    public String getEnrichedContent() {
        return enrichedContent;
    }

    public void setEnrichedContent(String enrichedContent) {
        this.enrichedContent = enrichedContent;
    }
}
