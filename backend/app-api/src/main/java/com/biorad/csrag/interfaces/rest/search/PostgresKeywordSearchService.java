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
import java.util.regex.Pattern;

@Component
@Primary
@ConditionalOnProperty(name = "spring.datasource.driver-class-name", havingValue = "org.postgresql.Driver")
public class PostgresKeywordSearchService implements KeywordSearchService {

    private static final Logger log = LoggerFactory.getLogger(PostgresKeywordSearchService.class);

    private static final String KOREAN_CHAR = "[\uAC00-\uD7A3]";
    private static final Pattern KOREAN_EOMI_PATTERN = Pattern.compile(
            "(?<=" + KOREAN_CHAR + ")(했습니다|되었습니다|합니다|입니다|됩니다|습니다|하세요|하여|하고|해서)");
    private static final Pattern KOREAN_JOSA_PATTERN = Pattern.compile(
            "(?<=" + KOREAN_CHAR + ")(에서|에게|으로|까지|부터|을|를|이|가|은|는|로|의|와|과|도|만)(?!["
            + "\uAC00-\uD7A3" + "])");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    private final JdbcTemplate jdbcTemplate;

    public PostgresKeywordSearchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    static String normalizeKorean(String query) {
        if (query == null || query.isBlank()) {
            return query;
        }
        String result = KOREAN_EOMI_PATTERN.matcher(query).replaceAll("");
        result = KOREAN_JOSA_PATTERN.matcher(result).replaceAll("");
        result = MULTI_SPACE.matcher(result).replaceAll(" ");
        return result.trim();
    }

    @Override
    public List<KeywordSearchResult> search(String query, int topK) {
        return search(query, topK, null);
    }

    @Override
    public List<KeywordSearchResult> search(String query, int topK, SearchFilter filter) {
        log.info("keyword.search.postgres query=\"{}\" topK={} filter={}", query, topK, filter);
        String normalizedQuery = normalizeKorean(query);
        if (!query.equals(normalizedQuery)) {
            log.info("keyword.search.postgres.normalized \"{}\" -> \"{}\"", query, normalizedQuery);
        }
        query = normalizedQuery;

        if (filter == null || filter.isEmpty()) {
            return searchWithoutFilter(query, topK);
        }

        StringBuilder sql = new StringBuilder("""
                SELECT id, document_id, content, source_type,
                       ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS rank
                FROM document_chunks
                WHERE content_tsv @@ plainto_tsquery('simple', ?)
                  AND (chunk_level IS NULL OR chunk_level = 'CHILD')
                """);

        List<Object> params = new ArrayList<>();
        params.add(query);
        params.add(query);

        // inquiryId 스코핑: 해당 문의 문서 + KB 전체 검색
        if (filter.inquiryId() != null && !filter.hasDocumentFilter()) {
            sql.append(" AND (document_id IN (SELECT id FROM documents WHERE inquiry_id = ?) OR source_type = 'KNOWLEDGE_BASE')");
            params.add(filter.inquiryId());
        }

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

        if (results.isEmpty()) {
            log.info("keyword.search.postgres.tsvector.empty, trying ILIKE fallback");
            results = fallbackIlikeSearch(query, topK, filter);
        }

        log.info("keyword.search.postgres.done results={}", results.size());
        return results;
    }

    private List<KeywordSearchResult> searchWithoutFilter(String query, int topK) {
        String sql = """
                SELECT id, document_id, content, source_type,
                       ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS rank
                FROM document_chunks
                WHERE content_tsv @@ plainto_tsquery('simple', ?)
                  AND (chunk_level IS NULL OR chunk_level = 'CHILD')
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

        if (results.isEmpty()) {
            log.info("keyword.search.postgres.tsvector.empty, trying ILIKE fallback");
            results = fallbackIlikeSearch(query, topK, null);
        }

        return results;
    }

    /**
     * ILIKE 기반 한국어 fallback 검색.
     * ts_vector가 0건일 때 사용. 쿼리를 공백 기준으로 분리하여 ILIKE 매칭.
     */
    private List<KeywordSearchResult> fallbackIlikeSearch(String query, int topK, SearchFilter filter) {
        String[] keywords = query.split("\\s+");
        if (keywords.length == 0) return List.of();

        // Filter out very short keywords (1 char) that cause too many matches
        List<String> significantKeywords = new ArrayList<>();
        for (String kw : keywords) {
            if (kw.length() >= 2) {
                significantKeywords.add(kw);
            }
        }
        if (significantKeywords.isEmpty()) {
            significantKeywords.addAll(List.of(keywords));
        }

        // Build ILIKE conditions — match ANY keyword, score by count
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT id, document_id, content, source_type, (");

        List<Object> params = new ArrayList<>();

        // Score = sum of matching keywords (normalized to 0-1)
        for (int i = 0; i < significantKeywords.size(); i++) {
            if (i > 0) sql.append(" + ");
            sql.append("CASE WHEN content ILIKE ? THEN 1 ELSE 0 END");
            params.add("%" + significantKeywords.get(i) + "%");
        }
        sql.append(")::float / ").append(significantKeywords.size()).append(" AS rank");
        sql.append(" FROM document_chunks WHERE (");

        // OR condition — at least one keyword must match
        for (int i = 0; i < significantKeywords.size(); i++) {
            if (i > 0) sql.append(" OR ");
            sql.append("content ILIKE ?");
            params.add("%" + significantKeywords.get(i) + "%");
        }
        sql.append(")");
        sql.append(" AND (chunk_level IS NULL OR chunk_level = 'CHILD')");

        // Apply filters
        if (filter != null && !filter.isEmpty()) {
            if (filter.inquiryId() != null && !filter.hasDocumentFilter()) {
                sql.append(" AND (document_id IN (SELECT id FROM documents WHERE inquiry_id = ?) OR source_type = 'KNOWLEDGE_BASE')");
                params.add(filter.inquiryId());
            }
            if (filter.hasDocumentFilter()) {
                String placeholders = String.join(",", filter.documentIds().stream().map(id -> "?").toList());
                sql.append(" AND document_id IN (").append(placeholders).append(")");
                params.addAll(filter.documentIds());
            }
            if (filter.hasProductFilter()) {
                String placeholders = String.join(",", filter.productFamilies().stream().map(pf -> "?").toList());
                sql.append(" AND product_family IN (").append(placeholders).append(")");
                params.addAll(filter.productFamilies());
            }
            if (filter.hasSourceTypeFilter()) {
                String placeholders = String.join(",", filter.sourceTypes().stream().map(s -> "?").toList());
                sql.append(" AND source_type IN (").append(placeholders).append(")");
                params.addAll(filter.sourceTypes());
            }
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

        log.info("keyword.search.ilike.fallback results={} keywords={}", results.size(), significantKeywords);
        return results;
    }
}
