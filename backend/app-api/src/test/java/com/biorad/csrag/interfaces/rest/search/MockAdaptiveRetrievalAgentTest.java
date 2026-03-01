package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MockAdaptiveRetrievalAgentTest {

    private HybridSearchService hybridSearchService;
    private RerankingService rerankingService;
    private MockAdaptiveRetrievalAgent agent;

    @BeforeEach
    void setUp() {
        hybridSearchService = mock(HybridSearchService.class);
        rerankingService = mock(RerankingService.class);
        agent = new MockAdaptiveRetrievalAgent(hybridSearchService, rerankingService);
    }

    @Test
    void retrieve_highScore_returnsSuccess() {
        UUID inquiryId = UUID.randomUUID();
        List<HybridSearchResult> candidates = List.of(candidate(0.9));
        List<RerankingService.RerankResult> rerankResults = List.of(rerankResult(0.9));

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(candidates);
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(rerankResults);

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("restriction enzyme 처리", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.SUCCESS);
        assertThat(result.evidences()).hasSize(1);
        assertThat(result.attempts()).isEqualTo(1);
    }

    @Test
    void retrieve_lowScore_returnsLowConfidence() {
        UUID inquiryId = UUID.randomUUID();
        List<HybridSearchResult> candidates = List.of(candidate(0.3));
        List<RerankingService.RerankResult> rerankResults = List.of(rerankResult(0.3));

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(candidates);
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(rerankResults);

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("obscure query", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.LOW_CONFIDENCE);
        assertThat(result.confidence()).isLessThan(0.5);
    }

    @Test
    void retrieve_emptyResults_returnsNoEvidence() {
        UUID inquiryId = UUID.randomUUID();

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(List.of());
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of());

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("unknown query", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.NO_EVIDENCE);
        assertThat(result.evidences()).isEmpty();
    }

    @Test
    void retrieve_usesForInquiryFilter() {
        UUID inquiryId = UUID.randomUUID();
        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(List.of());
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of());

        agent.retrieve("question", "", inquiryId);

        verify(hybridSearchService).search(eq("question"), eq(20), argThat(f -> inquiryId.equals(f.inquiryId())));
    }

    private HybridSearchResult candidate(double score) {
        return new HybridSearchResult(UUID.randomUUID(), UUID.randomUUID(), "content", score, 0.0, score, "INQUIRY", "VECTOR");
    }

    private RerankingService.RerankResult rerankResult(double score) {
        return new RerankingService.RerankResult(UUID.randomUUID(), UUID.randomUUID(), "content", score, score, "INQUIRY", "VECTOR");
    }
}
