ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS parent_chunk_id UUID;
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS chunk_level VARCHAR(10) DEFAULT 'CHILD';

CREATE INDEX IF NOT EXISTS idx_chunks_parent_id ON document_chunks(parent_chunk_id);
CREATE INDEX IF NOT EXISTS idx_chunks_level ON document_chunks(chunk_level);
