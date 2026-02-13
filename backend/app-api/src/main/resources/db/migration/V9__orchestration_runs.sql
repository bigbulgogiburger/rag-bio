CREATE TABLE IF NOT EXISTS orchestration_runs (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL,
    step VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    latency_ms BIGINT NOT NULL,
    error_message VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_orchestration_runs_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id)
);

CREATE INDEX IF NOT EXISTS idx_orchestration_runs_inquiry_created ON orchestration_runs(inquiry_id, created_at DESC);
