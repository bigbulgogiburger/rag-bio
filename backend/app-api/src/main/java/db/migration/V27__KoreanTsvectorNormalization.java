package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Statement;

public class V27__KoreanTsvectorNormalization extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        String dbProduct = conn.getMetaData().getDatabaseProductName();
        if (!"PostgreSQL".equalsIgnoreCase(dbProduct)) {
            return; // H2 no-op
        }

        try (Statement stmt = conn.createStatement()) {
            // 1. Create reusable normalize_korean() SQL function
            stmt.execute("""
                CREATE OR REPLACE FUNCTION normalize_korean(input TEXT) RETURNS TEXT AS $$
                DECLARE
                    result TEXT;
                BEGIN
                    result := coalesce(input, '');
                    -- Remove Korean 어미 (longer patterns first)
                    result := regexp_replace(result, '([\uAC00-\uD7A3])(했습니다|되었습니다)', '\\1', 'g');
                    result := regexp_replace(result, '([\uAC00-\uD7A3])(합니다|입니다|됩니다|습니다)', '\\1', 'g');
                    result := regexp_replace(result, '([\uAC00-\uD7A3])(하세요|하여|하고|해서)', '\\1', 'g');
                    -- Remove Korean 조사 (longer patterns first)
                    result := regexp_replace(result, '([\uAC00-\uD7A3])(에서|에게|으로|까지|부터)', '\\1', 'g');
                    result := regexp_replace(result, '([\uAC00-\uD7A3])(을|를|이|가|은|는|로|의|와|과|도|만)(?![\uAC00-\uD7A3])', '\\1', 'g');
                    -- Collapse multiple spaces
                    result := regexp_replace(result, '\\s{2,}', ' ', 'g');
                    result := trim(result);
                    RETURN result;
                END
                $$ LANGUAGE plpgsql IMMUTABLE
                """);

            // 2. Replace trigger function to normalize before tsvector creation
            stmt.execute("""
                CREATE OR REPLACE FUNCTION document_chunks_tsv_trigger() RETURNS trigger AS $$
                DECLARE
                    normalized TEXT;
                BEGIN
                    normalized := normalize_korean(coalesce(NEW.content, ''));
                    NEW.content_tsv := to_tsvector('simple', normalized);
                    RETURN NEW;
                END
                $$ LANGUAGE plpgsql
                """);

            // 3. Re-compute tsvector for all existing rows using normalized content
            stmt.execute(
                "UPDATE document_chunks SET content_tsv = to_tsvector('simple', normalize_korean(coalesce(content, '')))"
            );
        }
    }
}
