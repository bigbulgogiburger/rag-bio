package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MockSearchToolAgentTest {

    private HybridSearchService hybridSearchService;
    private RerankingService rerankingService;
    private MockSearchToolAgent agent;

    @BeforeEach
    void setUp() {
        hybridSearchService = mock(HybridSearchService.class);
        rerankingService = mock(RerankingService.class);
        agent = new MockSearchToolAgent(hybridSearchService, rerankingService);
    }

    @Test
    void agenticSearch_withInquiryId_usesForInquiryFilter() {
        UUID inquiryId = UUID.randomUUID();
        List<HybridSearchResult> candidates = List.of(candidate(0.8));
        List<RerankingService.RerankResult> results = List.of(rerankResult(0.8));

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(candidates);
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(results);

        List<RerankingService.RerankResult> actual = agent.agenticSearch("restriction enzyme", inquiryId);

        assertThat(actual).hasSize(1);
        verify(hybridSearchService).search(eq("restriction enzyme"), eq(20), argThat(f -> inquiryId.equals(f.inquiryId())));
        verify(rerankingService).rerank(eq("restriction enzyme"), anyList(), eq(10));
    }

    @Test
    void agenticSearch_withNullInquiryId_usesNoneFilter() {
        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(List.of());
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of());

        List<RerankingService.RerankResult> actual = agent.agenticSearch("question", null);

        assertThat(actual).isEmpty();
        verify(hybridSearchService).search(anyString(), anyInt(), argThat(f -> f.inquiryId() == null));
    }

    @Test
    void agenticSearch_emptyResults_returnsEmpty() {
        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(List.of());
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of());

        List<RerankingService.RerankResult> actual = agent.agenticSearch("no match", UUID.randomUUID());

        assertThat(actual).isEmpty();
    }

    private HybridSearchResult candidate(double score) {
        return new HybridSearchResult(UUID.randomUUID(), UUID.randomUUID(), "content", score, 0.0, score, "INQUIRY", "VECTOR");
    }

    private RerankingService.RerankResult rerankResult(double score) {
        return new RerankingService.RerankResult(UUID.randomUUID(), UUID.randomUUID(), "content", score, score, "INQUIRY", "VECTOR");
    }
}
