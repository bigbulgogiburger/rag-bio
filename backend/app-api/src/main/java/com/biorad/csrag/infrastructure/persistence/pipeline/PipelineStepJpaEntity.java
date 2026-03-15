package com.biorad.csrag.infrastructure.persistence.pipeline;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pipeline_steps")
public class PipelineStepJpaEntity {

    @Id
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "step_name", nullable = false, length = 30)
    private String stepName;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected PipelineStepJpaEntity() {}

    public PipelineStepJpaEntity(UUID id, UUID executionId, String stepName) {
        this.id = id;
        this.executionId = executionId;
        this.stepName = stepName;
        this.status = "PENDING";
        this.updatedAt = Instant.now();
    }

    public void updateStatus(String status, String message) {
        this.status = status;
        this.message = message;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public String getStepName() { return stepName; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public Instant getUpdatedAt() { return updatedAt; }
}
