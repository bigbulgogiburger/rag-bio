ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS context_prefix TEXT;
ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS enriched_content TEXT;
