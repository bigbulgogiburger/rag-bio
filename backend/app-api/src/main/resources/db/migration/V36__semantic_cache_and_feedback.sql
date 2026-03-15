-- V36__semantic_cache_and_feedback.sql

-- Semantic cache table
CREATE TABLE IF NOT EXISTS semantic_cache (
    id BIGSERIAL PRIMARY KEY,
    query_text TEXT NOT NULL,
    query_embedding_hash VARCHAR(64) NOT NULL,
    answer_text TEXT NOT NULL,
    answer_metadata TEXT,
    inquiry_id BIGINT,
    hit_count INTEGER DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    expires_at TIMESTAMP WITH TIME ZONE,
    invalidated BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_semantic_cache_hash UNIQUE (query_embedding_hash)
);

CREATE INDEX idx_semantic_cache_created ON semantic_cache(created_at);
CREATE INDEX idx_semantic_cache_expires ON semantic_cache(expires_at);

-- Answer feedback table
CREATE TABLE IF NOT EXISTS answer_feedback (
    id BIGSERIAL PRIMARY KEY,
    inquiry_id BIGINT NOT NULL,
    answer_id BIGINT NOT NULL,
    rating VARCHAR(30) NOT NULL,
    issues TEXT,
    comment TEXT,
    submitted_by VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_feedback_inquiry ON answer_feedback(inquiry_id);
CREATE INDEX idx_feedback_rating ON answer_feedback(rating);
