package com.biorad.csrag.interfaces.rest.search;

import java.util.List;
import java.util.UUID;

/**
 * Cross-Encoder 리랭킹 서비스.
 * 하이브리드 검색 결과를 query-document 관련성 기준으로 재정렬.
 */
public interface RerankingService {

    /**
     * 검색 후보를 리랭킹하여 상위 topK개 반환.
     *
     * @param query      사용자 검색 쿼리
     * @param candidates 하이브리드 검색 결과 후보 (Top-50)
     * @param topK       반환할 상위 결과 수
     * @return 리랭킹된 결과 (rerankScore 기준 내림차순)
     */
    List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topK);

    record RerankResult(
            UUID chunkId,
            UUID documentId,
            String content,
            double originalScore,
            double rerankScore,
            String sourceType,
            String matchSource
    ) {}
}
