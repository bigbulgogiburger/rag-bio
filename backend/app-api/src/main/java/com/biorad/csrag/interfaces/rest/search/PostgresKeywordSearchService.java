package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
        log.info("keyword.search.postgres query=\"{}\" topK={}", query, topK);

        String sql = """
                SELECT id, document_id, content, source_type,
                       ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS rank
                FROM document_chunks
                WHERE content_tsv @@ plainto_tsquery('simple', ?)
                ORDER BY rank DESC
                LIMIT ?
                """;

        List<KeywordSearchResult> results = jdbcTemplate.query(sql, (rs, rowNum) ->
                new KeywordSearchResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("content"),
                        rs.getDouble("rank"),
                        rs.getString("source_type")
                ), query, query, topK);

        log.info("keyword.search.postgres.done results={}", results.size());
        return results;
    }
}
