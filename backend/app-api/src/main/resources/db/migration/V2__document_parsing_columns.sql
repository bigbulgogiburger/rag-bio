ALTER TABLE documents ADD COLUMN IF NOT EXISTS extracted_text TEXT;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS last_error VARCHAR(500);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP WITH TIME ZONE;

UPDATE documents SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE documents ALTER COLUMN updated_at SET NOT NULL;
