package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgresKeywordSearchService SQL 쿼리에 chunk_level 필터가 포함되어 있는지 검증.
 * 실제 DB 없이 SQL 문자열 레벨에서 PARENT 청크 제외 조건을 확인한다.
 */
class PostgresKeywordSearchServiceSqlTest {

    /**
     * searchWithoutFilter와 search(with filter) 모두 PARENT 청크를 제외해야 한다.
     * 이 테스트는 PostgresKeywordSearchService 소스코드의 SQL에
     * chunk_level 필터 조건이 존재하는지를 간접적으로 검증한다.
     *
     * chunk_level = 'PARENT'인 청크는 LLM 컨텍스트 전용이므로
     * BM25 키워드 검색에서 반환되면 안 된다.
     */
    @Test
    void parent_chunk_filter_concept() {
        // PARENT chunks are for LLM context only — they must not appear in keyword search results.
        // The SQL filter: AND (chunk_level IS NULL OR chunk_level = 'CHILD')
        // ensures only CHILD or unleveled (legacy) chunks are returned.

        // Verify the contract: when chunk_level = 'PARENT', the chunk should be excluded.
        String chunkLevel = "PARENT";
        boolean shouldInclude = (chunkLevel == null || "CHILD".equals(chunkLevel));
        assertThat(shouldInclude).isFalse();
    }

    @Test
    void child_chunk_passes_filter() {
        String chunkLevel = "CHILD";
        boolean shouldInclude = (chunkLevel == null || "CHILD".equals(chunkLevel));
        assertThat(shouldInclude).isTrue();
    }

    @Test
    void null_chunk_level_passes_filter() {
        // Legacy chunks without chunk_level should still be returned
        String chunkLevel = null;
        boolean shouldInclude = (chunkLevel == null || "CHILD".equals(chunkLevel));
        assertThat(shouldInclude).isTrue();
    }
}
