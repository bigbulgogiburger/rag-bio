package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Mock 리랭킹: fusedScore를 rerankScore로 사용하여 기존 순서 유지.
 */
@Service
public class MockRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(MockRerankingService.class);

    @Override
    public List<RerankResult> rerank(String query, List<HybridSearchResult> candidates, int topK) {
        log.info("mock.rerank: returning top-{} by fusedScore (candidates={})", topK, candidates.size());
        return candidates.stream()
                .map(c -> new RerankResult(
                        c.chunkId(), c.documentId(), c.content(),
                        c.fusedScore(), c.fusedScore(),
                        c.sourceType(), c.matchSource()
                ))
                .sorted(Comparator.comparingDouble(RerankResult::rerankScore).reversed())
                .limit(topK)
                .toList();
    }
}
