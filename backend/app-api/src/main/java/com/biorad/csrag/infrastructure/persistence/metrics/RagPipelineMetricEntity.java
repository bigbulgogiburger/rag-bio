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
}
