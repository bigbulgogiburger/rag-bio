package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

public class V23_1__HybridSearchTsvectorPostgres extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        // Check if running on PostgreSQL
        String dbProduct = conn.getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equalsIgnoreCase(dbProduct)) {
            // Skip tsvector setup on H2 or other DBs
            return;
        }

        try (Statement stmt = conn.createStatement()) {
            // Add tsvector column if not exists
            stmt.execute("ALTER TABLE document_chunks ADD COLUMN IF NOT EXISTS content_tsv tsvector");

            // Populate existing rows
            stmt.execute("UPDATE document_chunks SET content_tsv = to_tsvector('simple', coalesce(content, '')) WHERE content_tsv IS NULL");

            // Create GIN index
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_chunks_content_tsv ON document_chunks USING GIN (content_tsv)");

            // Create trigger function for auto-update
            stmt.execute("""
                CREATE OR REPLACE FUNCTION document_chunks_tsv_trigger() RETURNS trigger AS $$
                BEGIN
                    NEW.content_tsv := to_tsvector('simple', coalesce(NEW.content, ''));
                    RETURN NEW;
                END
                $$ LANGUAGE plpgsql
                """);

            // Create trigger (drop first to be idempotent)
            stmt.execute("DROP TRIGGER IF EXISTS trg_chunks_tsv ON document_chunks");
            stmt.execute("""
                CREATE TRIGGER trg_chunks_tsv
                BEFORE INSERT OR UPDATE OF content ON document_chunks
                FOR EACH ROW EXECUTE FUNCTION document_chunks_tsv_trigger()
                """);
        }
    }
}
