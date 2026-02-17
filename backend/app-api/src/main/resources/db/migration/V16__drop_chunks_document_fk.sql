-- document_chunks 테이블은 documents (문의 첨부)와 knowledge_documents (지식 기반) 양쪽에서 사용
-- source_type + source_id 컬럼으로 출처를 구분하므로, documents만 참조하는 FK를 제거
ALTER TABLE document_chunks DROP CONSTRAINT IF EXISTS fk_document_chunks_document;
