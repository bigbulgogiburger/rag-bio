-- V26: document_chunks에 product_family 컬럼 추가
-- 제품별 검색 필터링을 위한 메타데이터

ALTER TABLE document_chunks ADD COLUMN product_family VARCHAR(100);
CREATE INDEX idx_chunks_product_family ON document_chunks(product_family);

-- answer_drafts에 질문 분해 관련 컬럼 추가
ALTER TABLE answer_drafts ADD COLUMN sub_question_count INTEGER DEFAULT 1;
ALTER TABLE answer_drafts ADD COLUMN decomposed_questions TEXT;
