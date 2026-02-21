package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.postgresql.Driver")
public class PostgresKeywordSearchService implements KeywordSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostgresKeywordSearchService.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresKeywordSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KeywordSearchResult> search(String query, int topK) {
        return search(query, topK, null);
    }

    @Override
    public List<KeywordSearchResult> search(String query, int topK, SearchFilter filter) {
        log.info("keyword.search.postgres query=\"{}\" topK={} filter={}", query, topK, filter);

        if (filter == null || filter.isEmpty()) {
            return searchWithoutFilter(query, topK);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, document_id, content, source_type,
                       ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS rank
                FROM document_chunks
                WHERE content_tsv @@ plainto_tsquery('simple', ?)
                """);

        List<Object> params = new ArrayList<>();
        params.add(query);
        params.add(query);

        if (filter.hasDocumentFilter()) {
            String placeholders = String.join(",",
                    filter.documentIds().stream().map(id -> "?").toList());
            sql.append(" AND document_id IN (").append(placeholders).append(")");
            params.addAll(filter.documentIds());
        }

        if (filter.hasProductFilter()) {
            sql.append(" AND product_family = ?");
            params.add(filter.productFamily());
        }

        if (filter.hasSourceTypeFilter()) {
            String placeholders = String.join(",",
                    filter.sourceTypes().stream().map(s -> "?").toList());
            sql.append(" AND source_type IN (").append(placeholders).append(")");
            params.addAll(filter.sourceTypes());
        }

        sql.append(" ORDER BY rank DESC LIMIT ?");
        params.add(topK);

        List<KeywordSearchResult> results = jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
                new KeywordSearchResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("content"),
                        rs.getDouble("rank"),
                        rs.getString("source_type")
                ), params.toArray());

        log.info("keyword.search.postgres.done results={}", results.size());
        return results;
    }

    private List<KeywordSearchResult> searchWithoutFilter(String query, int topK) {
        String sql = """
                SELECT id, document_id, content, source_type,
                       ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS rank
                FROM document_chunks
                WHERE content_tsv @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new KeywordSearchResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("content"),
                        rs.getDouble("rank"),
                        rs.getString("source_type")
                ), query, query, topK);
    }
}
