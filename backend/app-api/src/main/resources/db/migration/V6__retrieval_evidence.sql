CREATE TABLE IF NOT EXISTS retrieval_evidence (
    id UUID PRIMARY KEY,
    inquiry_id UUID NOT NULL,
    chunk_id UUID NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    rank_order INT NOT NULL,
    question VARCHAR(4000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_retrieval_inquiry FOREIGN KEY (inquiry_id) REFERENCES inquiries(id)
);

CREATE INDEX IF NOT EXISTS idx_retrieval_inquiry_created ON retrieval_evidence(inquiry_id, created_at);
CREATE INDEX IF NOT EXISTS idx_retrieval_chunk ON retrieval_evidence(chunk_id);
