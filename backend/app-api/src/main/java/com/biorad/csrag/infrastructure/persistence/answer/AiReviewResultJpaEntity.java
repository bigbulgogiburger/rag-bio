package com.biorad.csrag.infrastructure.persistence.answer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_review_results")
public class AiReviewResultJpaEntity {

    @Id
    private UUID id;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "decision", nullable = false, length = 32)
    private String decision;

    @Column(name = "score", nullable = false)
    private int score;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    @Column(name = "revised_draft", columnDefinition = "TEXT")
    private String revisedDraft;

    @Column(name = "issues", nullable = false, columnDefinition = "TEXT")
    private String issues;

    @Column(name = "gate_results", columnDefinition = "TEXT")
    private String gateResults;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiReviewResultJpaEntity() {}

    public AiReviewResultJpaEntity(UUID id, UUID answerId, UUID inquiryId, String decision, int score,
                                    String summary, String revisedDraft, String issues, String gateResults,
                                    Instant createdAt) {
        this.id = id;
        this.answerId = answerId;
        this.inquiryId = inquiryId;
        this.decision = decision;
        this.score = score;
        this.summary = summary;
        this.revisedDraft = revisedDraft;
        this.issues = issues;
        this.gateResults = gateResults;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getAnswerId() { return answerId; }
    public UUID getInquiryId() { return inquiryId; }
    public String getDecision() { return decision; }
    public int getScore() { return score; }
    public String getSummary() { return summary; }
    public String getRevisedDraft() { return revisedDraft; }
    public String getIssues() { return issues; }
    public String getGateResults() { return gateResults; }
    public Instant getCreatedAt() { return createdAt; }
}
