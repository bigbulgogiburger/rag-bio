package com.biorad.csrag.interfaces.rest.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Mock SearchToolAgent: LLM 없이 단순 HybridSearch + Reranking 실행.
 * OpenAI 비활성화 시 자동 사용.
 */
@Service
@ConditionalOnMissingBean(OpenAiSearchToolAgent.class)
public class MockSearchToolAgent implements SearchToolAgent {

    private static final Logger log = LoggerFactory.getLogger(MockSearchToolAgent.class);

    private static final int SEARCH_TOP_K = 20;
    private static final int FINAL_TOP_K = 10;

    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;

    public MockSearchToolAgent(HybridSearchService hybridSearchService, RerankingService rerankingService) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
    }

    @Override
    public List<RerankingService.RerankResult> agenticSearch(String question, UUID inquiryId) {
        log.info("mock.search-tool-agent: bypassing LLM tool selection, direct hybrid search");
        SearchFilter filter = inquiryId != null
                ? SearchFilter.forInquiry(inquiryId)
                : SearchFilter.none();
        List<HybridSearchResult> results = hybridSearchService.search(question, SEARCH_TOP_K, filter);
        return rerankingService.rerank(question, results, FINAL_TOP_K);
    }
}
