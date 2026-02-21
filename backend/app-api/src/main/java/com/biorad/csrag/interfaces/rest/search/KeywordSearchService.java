package com.biorad.csrag.interfaces.rest.search;

import java.util.List;

public interface KeywordSearchService {

    List<KeywordSearchResult> search(String query, int topK);

    /**
     * 필터 적용 키워드 검색
     *
     * @param query  검색 쿼리
     * @param topK   상위 K개 결과
     * @param filter 검색 필터 (문서 ID, 제품 패밀리 등)
     */
    default List<KeywordSearchResult> search(String query, int topK, SearchFilter filter) {
        return search(query, topK);
    }
}
