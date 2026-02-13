package com.biorad.csrag.infrastructure.persistence.orchestration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orchestration_runs")
public class OrchestrationRunJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "step", nullable = false, length = 32)
    private String step;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrchestrationRunJpaEntity() {}

    public OrchestrationRunJpaEntity(UUID id, UUID inquiryId, String step, String status, long latencyMs, String errorMessage, Instant createdAt) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.step = step;
        this.status = status;
        this.latencyMs = latencyMs;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public String getStep() { return step; }
    public String getStatus() { return status; }
    public long getLatencyMs() { return latencyMs; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
