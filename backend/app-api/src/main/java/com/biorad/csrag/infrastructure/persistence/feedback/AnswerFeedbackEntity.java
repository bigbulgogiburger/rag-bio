package com.biorad.csrag.infrastructure.persistence.feedback;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "answer_feedback")
public class AnswerFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Column(name = "answer_id", nullable = false)
    private Long answerId;

    @Column(name = "rating", nullable = false, length = 30)
    private String rating;

    @Column(name = "issues", columnDefinition = "TEXT")
    private String issues;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "submitted_by", length = 100)
    private String submittedBy;

    @Column(name = "created_at")
    private Instant createdAt;

    protected AnswerFeedbackEntity() {}

    public AnswerFeedbackEntity(Long inquiryId, Long answerId, String rating,
                                 String issues, String comment, String submittedBy) {
        this.inquiryId = inquiryId;
        this.answerId = answerId;
        this.rating = rating;
        this.issues = issues;
        this.comment = comment;
        this.submittedBy = submittedBy;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInquiryId() { return inquiryId; }
    public Long getAnswerId() { return answerId; }
    public String getRating() { return rating; }
    public String getIssues() { return issues; }
    public String getComment() { return comment; }
    public String getSubmittedBy() { return submittedBy; }
    public Instant getCreatedAt() { return createdAt; }
}
