CREATE TABLE IF NOT EXISTS send_attempt_logs (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL,
    answer_id UUID NOT NULL,
    send_request_id VARCHAR(120),
    outcome VARCHAR(40) NOT NULL,
    detail VARCHAR(255),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_send_attempt_logs_outcome_created_at
    ON send_attempt_logs (outcome, created_at DESC);
