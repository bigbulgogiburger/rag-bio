package com.biorad.csrag.infrastructure.persistence.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "rag_pipeline_metrics")
public class RagPipelineMetricEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inquiry_id")
    private Long inquiryId;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(name = "metric_value", nullable = false)
    private double metricValue;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // ── Token tracking columns (V35) ──────────────────────────

    @Column(name = "total_prompt_tokens")
    private Integer totalPromptTokens;

    @Column(name = "total_completion_tokens")
    private Integer totalCompletionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    @Column(name = "estimated_cost_usd")
    private Double estimatedCostUsd;

    @Column(name = "token_usage_detail", columnDefinition = "TEXT")
    private String tokenUsageDetail;

    protected RagPipelineMetricEntity() {}

    public RagPipelineMetricEntity(Long inquiryId, String metricType, double metricValue, String details) {
        this.inquiryId = inquiryId;
        this.metricType = metricType;
        this.metricValue = metricValue;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getInquiryId() { return inquiryId; }
    public String getMetricType() { return metricType; }
    public double getMetricValue() { return metricValue; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }

    // ── Token tracking getters/setters ────────────────────────

    public Integer getTotalPromptTokens() { return totalPromptTokens; }
    public void setTotalPromptTokens(Integer totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }

    public Integer getTotalCompletionTokens() { return totalCompletionTokens; }
    public void setTotalCompletionTokens(Integer totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }

    public Integer getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Integer totalTokens) { this.totalTokens = totalTokens; }

    public Double getEstimatedCostUsd() { return estimatedCostUsd; }
    public void setEstimatedCostUsd(Double estimatedCostUsd) { this.estimatedCostUsd = estimatedCostUsd; }

    public String getTokenUsageDetail() { return tokenUsageDetail; }
    public void setTokenUsageDetail(String tokenUsageDetail) { this.tokenUsageDetail = tokenUsageDetail; }
}
