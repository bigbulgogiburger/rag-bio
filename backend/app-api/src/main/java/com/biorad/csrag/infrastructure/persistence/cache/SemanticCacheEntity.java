package com.biorad.csrag.infrastructure.persistence.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "semantic_cache")
public class SemanticCacheEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "query_text", nullable = false, columnDefinition = "TEXT")
    private String queryText;

    @Column(name = "query_embedding_hash", nullable = false, length = 64)
    private String queryEmbeddingHash;

    @Column(name = "answer_text", nullable = false, columnDefinition = "TEXT")
    private String answerText;

    @Column(name = "answer_metadata", columnDefinition = "TEXT")
    private String answerMetadata;

    @Column(name = "inquiry_id")
    private Long inquiryId;

    @Column(name = "hit_count")
    private int hitCount = 0;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "invalidated")
    private boolean invalidated = false;

    public SemanticCacheEntity() {}

    public Long getId() { return id; }
    public String getQueryText() { return queryText; }
    public String getQueryEmbeddingHash() { return queryEmbeddingHash; }
    public String getAnswerText() { return answerText; }
    public String getAnswerMetadata() { return answerMetadata; }
    public Long getInquiryId() { return inquiryId; }
    public int getHitCount() { return hitCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isInvalidated() { return invalidated; }

    public void setQueryText(String queryText) { this.queryText = queryText; }
    public void setQueryEmbeddingHash(String queryEmbeddingHash) { this.queryEmbeddingHash = queryEmbeddingHash; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }
    public void setAnswerMetadata(String answerMetadata) { this.answerMetadata = answerMetadata; }
    public void setInquiryId(Long inquiryId) { this.inquiryId = inquiryId; }
    public void setHitCount(int hitCount) { this.hitCount = hitCount; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public void setInvalidated(boolean invalidated) { this.invalidated = invalidated; }
}
