package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.h2.Driver", matchIfMissing = true)
public class MockKeywordSearchService implements KeywordSearchService {

    private static final Logger log = LoggerFactory.getLogger(MockKeywordSearchService.class);

    private final JdbcTemplate jdbcTemplate;

    public MockKeywordSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KeywordSearchResult> search(String query, int topK) {
        log.info("keyword.search.mock query=\"{}\" topK={}", query, topK);

        String likePattern = "%" + query.toLowerCase() + "%";
        String sql = "SELECT id, document_id, content, source_type FROM document_chunks WHERE LOWER(content) LIKE ? LIMIT ?";

        List<KeywordSearchResult> results = jdbcTemplate.query(sql, (rs, rowNum) ->
                new KeywordSearchResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("content"),
                        0.5,
                        rs.getString("source_type")
                ), likePattern, topK);

        log.info("keyword.search.mock.done results={}", results.size());
        return results;
    }
}
