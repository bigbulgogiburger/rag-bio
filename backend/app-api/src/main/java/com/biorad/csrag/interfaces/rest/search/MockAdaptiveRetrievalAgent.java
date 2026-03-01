package com.biorad.csrag.interfaces.rest.search;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnMissingBean(OpenAiAdaptiveRetrievalAgent.class)
public class MockAdaptiveRetrievalAgent implements AdaptiveRetrievalAgent {

    private final HybridSearchService hybridSearchService;
    private final RerankingService rerankingService;

    public MockAdaptiveRetrievalAgent(HybridSearchService hybridSearchService,
                                       RerankingService rerankingService) {
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
    }

    @Override
    public AdaptiveResult retrieve(String question, String productContext, UUID inquiryId) {
        SearchFilter filter = SearchFilter.forInquiry(inquiryId);
        List<HybridSearchResult> candidates = hybridSearchService.search(question, 20, filter);
        List<RerankingService.RerankResult> results = rerankingService.rerank(question, candidates, 10);

        if (results.isEmpty()) {
            return AdaptiveResult.noEvidence(question);
        }
        double topScore = results.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);
        if (topScore >= 0.5) {
            return AdaptiveResult.success(results, 1);
        }
        return AdaptiveResult.lowConfidence(results, topScore);
    }
}
