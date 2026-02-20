-- answer_drafts 테이블에 AI 리뷰/승인 결과 컬럼 추가
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS review_score INT;
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS review_decision VARCHAR(32);
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS approval_decision VARCHAR(32);
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS approval_reason VARCHAR(2000);

-- AI 리뷰 상세 결과 테이블
CREATE TABLE IF NOT EXISTS ai_review_results (
    id UUID PRIMARY KEY,
    answer_id UUID NOT NULL REFERENCES answer_drafts(id),
    inquiry_id UUID NOT NULL REFERENCES inquiries(id),
    decision VARCHAR(32) NOT NULL,
    score INT NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    revised_draft TEXT,
    issues TEXT NOT NULL DEFAULT '[]',
    gate_results TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_review_results_answer_id ON ai_review_results(answer_id);
