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

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "review_comment", length = 2000)
    private String reviewComment;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approve_comment", length = 2000)
    private String approveComment;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "sent_by", length = 120)
    private String sentBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "send_channel", length = 32)
    private String sendChannel;

    @Column(name = "send_message_id", length = 255)
    private String sendMessageId;

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
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getApprovedBy() { return approvedBy; }
    public String getApproveComment() { return approveComment; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getSentBy() { return sentBy; }
    public Instant getSentAt() { return sentAt; }
    public String getSendChannel() { return sendChannel; }
    public String getSendMessageId() { return sendMessageId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markReviewed(String reviewer, String comment) {
        this.status = "REVIEWED";
        this.reviewedBy = reviewer;
        this.reviewComment = comment;
        this.reviewedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markApproved(String approver, String comment) {
        this.status = "APPROVED";
        this.approvedBy = approver;
        this.approveComment = comment;
        this.approvedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markSent(String sender, String channel, String messageId) {
        this.status = "SENT";
        this.sentBy = sender;
        this.sendChannel = channel;
        this.sendMessageId = messageId;
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
