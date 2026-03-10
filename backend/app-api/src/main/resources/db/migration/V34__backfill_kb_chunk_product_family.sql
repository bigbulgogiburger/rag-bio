-- V34: Backfill product_family for existing KB chunks from their parent KB documents
UPDATE document_chunks dc
SET product_family = (
    SELECT kd.product_family
    FROM knowledge_documents kd
    WHERE kd.id = dc.source_id
      AND kd.product_family IS NOT NULL
)
WHERE dc.source_type = 'KNOWLEDGE_BASE'
  AND (dc.product_family IS NULL OR dc.product_family = '')
  AND EXISTS (
    SELECT 1
    FROM knowledge_documents kd
    WHERE kd.id = dc.source_id
      AND kd.product_family IS NOT NULL
  );
