package com.biorad.csrag.infrastructure.persistence.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_executions")
public class PipelineExecutionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "inquiry_id", nullable = false)
    private UUID inquiryId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    protected PipelineExecutionJpaEntity() {}

    public PipelineExecutionJpaEntity(UUID id, UUID inquiryId) {
        this.id = id;
        this.inquiryId = inquiryId;
        this.status = "GENERATING";
        this.startedAt = Instant.now();
        this.createdAt = Instant.now();
    }

    public void markCompleted() {
        this.status = "COMPLETED";
        this.completedAt = Instant.now();
    }

    public void markFailed(String error) {
        this.status = "FAILED";
        this.errorMessage = error;
        this.completedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getInquiryId() { return inquiryId; }
    public String getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
