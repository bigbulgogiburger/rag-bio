-- V21: draft와 citations 컬럼을 TEXT로 변환 (긴 답변/근거 저장 지원)
ALTER TABLE answer_drafts ALTER COLUMN draft TYPE TEXT;
ALTER TABLE answer_drafts ALTER COLUMN citations TYPE TEXT;
