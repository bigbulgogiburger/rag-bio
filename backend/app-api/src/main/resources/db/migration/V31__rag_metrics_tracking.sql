CREATE TABLE IF NOT EXISTS rag_pipeline_metrics (
    id BIGSERIAL PRIMARY KEY,
    inquiry_id BIGINT,
    metric_type VARCHAR(50) NOT NULL,
    metric_value DOUBLE PRECISION NOT NULL,
    details TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rpm_inquiry ON rag_pipeline_metrics(inquiry_id);
CREATE INDEX IF NOT EXISTS idx_rpm_type_created ON rag_pipeline_metrics(metric_type, created_at);
