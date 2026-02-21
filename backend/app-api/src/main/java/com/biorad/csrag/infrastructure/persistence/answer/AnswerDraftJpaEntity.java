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

    @Column(name = "draft", nullable = false, columnDefinition = "TEXT")
    private String draft;

    @Column(name = "citations", nullable = false, columnDefinition = "TEXT")
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

    @Column(name = "send_request_id", length = 120)
    private String sendRequestId;

    @Column(name = "review_score")
    private Integer reviewScore;

    @Column(name = "review_decision", length = 32)
    private String reviewDecision;

    @Column(name = "approval_decision", length = 32)
    private String approvalDecision;

    @Column(name = "approval_reason", length = 2000)
    private String approvalReason;

    @Column(name = "previous_answer_id")
    private UUID previousAnswerId;

    @Column(name = "refinement_count", nullable = false)
    private int refinementCount;

    @Column(name = "additional_instructions", length = 2000)
    private String additionalInstructions;

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
    public String getSendRequestId() { return sendRequestId; }
    public Integer getReviewScore() { return reviewScore; }
    public String getReviewDecision() { return reviewDecision; }
    public String getApprovalDecision() { return approvalDecision; }
    public String getApprovalReason() { return approvalReason; }
    public UUID getPreviousAnswerId() { return previousAnswerId; }
    public int getRefinementCount() { return refinementCount; }
    public String getAdditionalInstructions() { return additionalInstructions; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setRefinementInfo(UUID previousAnswerId, int refinementCount, String additionalInstructions) {
        this.previousAnswerId = previousAnswerId;
        this.refinementCount = refinementCount;
        this.additionalInstructions = additionalInstructions;
    }

    public void markAiReviewed(String reviewer, int score, String decision, String comment) {
        this.status = "REVIEWED";
        this.reviewedBy = reviewer;
        this.reviewComment = comment;
        this.reviewedAt = Instant.now();
        this.reviewScore = score;
        this.reviewDecision = decision;
        this.updatedAt = Instant.now();
    }

    public void markAiApproved(String decision, String reason) {
        this.approvalDecision = decision;
        this.approvalReason = reason;
        if ("AUTO_APPROVED".equals(decision)) {
            this.status = "APPROVED";
            this.approvedBy = "ai-approval-agent";
            this.approvedAt = Instant.now();
        }
        if ("REJECTED".equals(decision)) {
            this.status = "DRAFT";
        }
        this.updatedAt = Instant.now();
    }

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

    public void updateDraft(String newDraft) {
        this.draft = newDraft;
        this.updatedAt = Instant.now();
    }

    public void markSent(String sender, String channel, String messageId, String requestId) {
        this.status = "SENT";
        this.sentBy = sender;
        this.sendChannel = channel;
        this.sendMessageId = messageId;
        this.sendRequestId = requestId;
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
