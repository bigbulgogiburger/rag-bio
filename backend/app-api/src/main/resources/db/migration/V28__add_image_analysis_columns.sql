-- Image analysis columns on documents table
ALTER TABLE documents ADD COLUMN image_analysis_type VARCHAR(20);
ALTER TABLE documents ADD COLUMN image_extracted_text TEXT;
ALTER TABLE documents ADD COLUMN image_visual_description TEXT;
ALTER TABLE documents ADD COLUMN image_technical_context TEXT;
ALTER TABLE documents ADD COLUMN image_suggested_query TEXT;
ALTER TABLE documents ADD COLUMN image_analysis_confidence DOUBLE PRECISION;
