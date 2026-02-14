-- Knowledge Base 문서 테이블 생성
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id UUID PRIMARY KEY,
    title VARCHAR(500) NOT NULL,
    category VARCHAR(100) NOT NULL,
    product_family VARCHAR(200),
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    status VARCHAR(40) NOT NULL,
    description VARCHAR(2000),
    tags VARCHAR(500),
    uploaded_by VARCHAR(120),
    extracted_text TEXT,
    ocr_confidence DOUBLE PRECISION,
    chunk_count INT,
    vector_count INT,
    last_error VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Knowledge Base 문서 검색 인덱스
CREATE INDEX IF NOT EXISTS idx_kb_docs_category ON knowledge_documents(category);
CREATE INDEX IF NOT EXISTS idx_kb_docs_product_family ON knowledge_documents(product_family);
CREATE INDEX IF NOT EXISTS idx_kb_docs_status ON knowledge_documents(status);
CREATE INDEX IF NOT EXISTS idx_kb_docs_created_at ON knowledge_documents(created_at);

-- document_chunks 테이블에 source 구분 컬럼 추가
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS source_type VARCHAR(20) DEFAULT 'INQUIRY';
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS source_id UUID;

-- 기존 데이터의 source_id를 document_id 값으로 세팅
-- (INQUIRY 타입의 경우 source_id = document_id)
UPDATE document_chunks SET source_id = document_id WHERE source_type = 'INQUIRY' AND source_id IS NULL;

-- source 기반 검색 인덱스
CREATE INDEX IF NOT EXISTS idx_chunks_source ON document_chunks(source_type, source_id);
