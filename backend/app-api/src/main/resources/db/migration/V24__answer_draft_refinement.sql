-- 답변 보완 요청 지원을 위한 컬럼 추가
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS previous_answer_id UUID;
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS refinement_count INT NOT NULL DEFAULT 0;
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS additional_instructions VARCHAR(2000);

CREATE INDEX IF NOT EXISTS idx_answer_drafts_previous ON answer_drafts(previous_answer_id);
