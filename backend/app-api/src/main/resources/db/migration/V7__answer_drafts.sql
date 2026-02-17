CREATE TABLE IF NOT EXISTS answer_drafts (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL,
    version INT NOT NULL,
    verdict VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    tone VARCHAR(32) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    draft VARCHAR(5000) NOT NULL,
    citations VARCHAR(5000) NOT NULL,
    risk_flags VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_answer_drafts_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_answer_drafts_inquiry_version ON answer_drafts(inquiry_id, version);
CREATE INDEX IF NOT EXISTS idx_answer_drafts_inquiry_created ON answer_drafts(inquiry_id, created_at DESC);
