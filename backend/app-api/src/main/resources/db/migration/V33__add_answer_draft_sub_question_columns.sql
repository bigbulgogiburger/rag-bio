-- V33: answer_drafts에 서브 질문 분해 관련 컬럼 추가
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS sub_question_count INT NOT NULL DEFAULT 1;
ALTER TABLE answer_drafts ADD COLUMN IF NOT EXISTS decomposed_questions TEXT;
