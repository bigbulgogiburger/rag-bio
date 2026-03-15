ALTER TABLE rag_pipeline_metrics ADD COLUMN IF NOT EXISTS total_prompt_tokens INTEGER DEFAULT 0;
ALTER TABLE rag_pipeline_metrics ADD COLUMN IF NOT EXISTS total_completion_tokens INTEGER DEFAULT 0;
ALTER TABLE rag_pipeline_metrics ADD COLUMN IF NOT EXISTS total_tokens INTEGER DEFAULT 0;
ALTER TABLE rag_pipeline_metrics ADD COLUMN IF NOT EXISTS estimated_cost_usd DOUBLE PRECISION DEFAULT 0.0;
ALTER TABLE rag_pipeline_metrics ADD COLUMN IF NOT EXISTS token_usage_detail TEXT; -- JSON string of per-step usage
