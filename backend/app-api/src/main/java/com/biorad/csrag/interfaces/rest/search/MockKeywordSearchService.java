package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
        return search(query, topK, null);
    }

    @Override
    public List<KeywordSearchResult> search(String query, int topK, SearchFilter filter) {
        log.info("keyword.search.mock query=\"{}\" topK={} filter={}", query, topK, filter);

        String likePattern = "%" + query.toLowerCase() + "%";

        if (filter == null || filter.isEmpty()) {
            return searchWithoutFilter(likePattern, topK);
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, document_id, content, source_type, product_family FROM document_chunks WHERE LOWER(content) LIKE ?");
        List<Object> params = new ArrayList<>();
        params.add(likePattern);

        if (filter.hasDocumentFilter()) {
            String placeholders = String.join(",",
                    filter.documentIds().stream().map(id -> "?").toList());
            sql.append(" AND document_id IN (").append(placeholders).append(")");
            params.addAll(filter.documentIds());
        }

        if (filter.hasProductFilter()) {
            String placeholdersPf = String.join(",",
                    filter.productFamilies().stream().map(pf -> "?").toList());
            sql.append(" AND product_family IN (").append(placeholdersPf).append(")");
            params.addAll(filter.productFamilies());
        }

        if (filter.hasSourceTypeFilter()) {
            String placeholders = String.join(",",
                    filter.sourceTypes().stream().map(s -> "?").toList());
            sql.append(" AND source_type IN (").append(placeholders).append(")");
            params.addAll(filter.sourceTypes());
        }

        sql.append(" LIMIT ?");
        params.add(topK);

        List<KeywordSearchResult> results = jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
                new KeywordSearchResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("content"),
                        0.5,
                        rs.getString("source_type")
                ), params.toArray());

        log.info("keyword.search.mock.done results={}", results.size());
        return results;
    }

    private List<KeywordSearchResult> searchWithoutFilter(String likePattern, int topK) {
        String sql = "SELECT id, document_id, content, source_type FROM document_chunks WHERE LOWER(content) LIKE ? LIMIT ?";

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new KeywordSearchResult(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("content"),
                        0.5,
                        rs.getString("source_type")
                ), likePattern, topK);
    }
}
