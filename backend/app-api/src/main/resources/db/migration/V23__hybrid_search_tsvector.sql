-- This migration adds full-text search support for document_chunks.
-- tsvector column and GIN index are PostgreSQL-specific.
-- On H2 (dev), this migration is a no-op.

-- PostgreSQL-specific DDL (tsvector, GIN index, trigger) is applied
-- via V23.1 Java-based migration which detects the database type.
SELECT 1;
