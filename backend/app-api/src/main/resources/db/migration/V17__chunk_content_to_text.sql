-- document_chunks.content 컬럼을 VARCHAR(4000) → TEXT로 변경
-- PDF/DOCX에서 추출된 긴 텍스트 블록이 4000자를 초과할 수 있음
ALTER TABLE document_chunks ALTER COLUMN content TYPE TEXT;
