-- 청크별 페이지 추적 컬럼 추가 (nullable: 비-PDF 및 레거시 데이터 호환)
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS page_start INT;
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS page_end INT;

-- 페이지 기반 검색을 위한 인덱스
CREATE INDEX IF NOT EXISTS idx_chunks_page_range
    ON document_chunks(document_id, page_start, page_end);
