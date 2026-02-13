package com.biorad.csrag.infrastructure.persistence.answer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "answer_drafts")
public class AnswerDraftJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "verdict", nullable = false, length = 32)
    private String verdict;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Column(name = "tone", nullable = false, length = 32)
    private String tone;

    @Column(name = "channel", nullable = false, length = 32)
    private String channel;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "draft", nullable = false, length = 5000)
    private String draft;

    @Column(name = "citations", nullable = false, length = 5000)
    private String citations;

    @Column(name = "risk_flags", nullable = false, length = 1000)
    private String riskFlags;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AnswerDraftJpaEntity() {}

    public AnswerDraftJpaEntity(UUID id, UUID inquiryId, int version, String verdict, double confidence, String tone, String channel, String status, String draft, String citations, String riskFlags, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.version = version;
        this.verdict = verdict;
        this.confidence = confidence;
        this.tone = tone;
        this.channel = channel;
        this.status = status;
        this.draft = draft;
        this.citations = citations;
        this.riskFlags = riskFlags;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public int getVersion() { return version; }
    public String getVerdict() { return verdict; }
    public double getConfidence() { return confidence; }
    public String getTone() { return tone; }
    public String getChannel() { return channel; }
    public String getStatus() { return status; }
    public String getDraft() { return draft; }
    public String getCitations() { return citations; }
    public String getRiskFlags() { return riskFlags; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markReviewed() {
        this.status = "REVIEWED";
        this.updatedAt = Instant.now();
    }

    public void markApproved() {
        this.status = "APPROVED";
        this.updatedAt = Instant.now();
    }
}
