package com.biorad.csrag.infrastructure.persistence.retrieval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "retrieval_evidence")
public class RetrievalEvidenceJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "chunk_id", nullable = false)
    private UUID chunkId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_order", nullable = false)
    private int rankOrder;

    @Column(name = "question", nullable = false, length = 4000)
    private String question;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RetrievalEvidenceJpaEntity() {}

    public RetrievalEvidenceJpaEntity(UUID id, UUID inquiryId, UUID chunkId, double score, int rankOrder, String question, Instant createdAt) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.chunkId = chunkId;
        this.score = score;
        this.rankOrder = rankOrder;
        this.question = question;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public UUID getChunkId() { return chunkId; }
    public double getScore() { return score; }
    public int getRankOrder() { return rankOrder; }
    public String getQuestion() { return question; }
    public Instant getCreatedAt() { return createdAt; }
}
