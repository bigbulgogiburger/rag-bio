package com.biorad.csrag.infrastructure.persistence.sendattempt;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "send_attempt_logs")
public class SendAttemptJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "answer_id", nullable = false)
    private UUID answerId;

    @Column(name = "send_request_id", length = 120)
    private String sendRequestId;

    @Column(name = "outcome", nullable = false, length = 40)
    private String outcome;

    @Column(name = "detail", length = 255)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SendAttemptJpaEntity() {}

    public SendAttemptJpaEntity(UUID id, UUID inquiryId, UUID answerId, String sendRequestId, String outcome, String detail, Instant createdAt) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.answerId = answerId;
        this.sendRequestId = sendRequestId;
        this.outcome = outcome;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public UUID getAnswerId() { return answerId; }
    public String getSendRequestId() { return sendRequestId; }
    public String getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
