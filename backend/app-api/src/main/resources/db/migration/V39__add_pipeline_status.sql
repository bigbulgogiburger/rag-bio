CREATE TABLE pipeline_executions (
    id              UUID PRIMARY KEY,
    inquiry_id      UUID NOT NULL REFERENCES inquiries(id),
    status          VARCHAR(20) NOT NULL DEFAULT 'GENERATING',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE TABLE pipeline_steps (
    id              UUID PRIMARY KEY,
    execution_id    UUID NOT NULL REFERENCES pipeline_executions(id),
    step_name       VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message         TEXT,
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_pipeline_exec_inquiry ON pipeline_executions(inquiry_id);
CREATE INDEX idx_pipeline_steps_exec ON pipeline_steps(execution_id);
