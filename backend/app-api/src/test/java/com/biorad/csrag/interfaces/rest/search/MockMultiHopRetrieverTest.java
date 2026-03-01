package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MockMultiHopRetrieverTest {

    private AdaptiveRetrievalAgent adaptiveAgent;
    private MockMultiHopRetriever retriever;

    @BeforeEach
    void setUp() {
        adaptiveAgent = mock(AdaptiveRetrievalAgent.class);
        retriever = new MockMultiHopRetriever(adaptiveAgent);
    }

    @Test
    void retrieve_delegatesToAdaptiveAgent_returnsSingleHop() {
        UUID inquiryId = UUID.randomUUID();
        List<RerankingService.RerankResult> evidences = List.of(rerankResult(0.9));
        AdaptiveRetrievalAgent.AdaptiveResult adaptive =
                AdaptiveRetrievalAgent.AdaptiveResult.success(evidences, 1);

        when(adaptiveAgent.retrieve(anyString(), anyString(), any())).thenReturn(adaptive);

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("QX700 + naica 호환성", inquiryId);

        assertThat(result.isSingleHop()).isTrue();
        assertThat(result.evidences()).hasSize(1);
        assertThat(result.hops()).isEmpty();
        verify(adaptiveAgent).retrieve(eq("QX700 + naica 호환성"), eq(""), eq(inquiryId));
    }

    @Test
    void retrieve_noEvidence_returnsSingleHopEmpty() {
        UUID inquiryId = UUID.randomUUID();
        AdaptiveRetrievalAgent.AdaptiveResult noEvidence =
                AdaptiveRetrievalAgent.AdaptiveResult.noEvidence("question");

        when(adaptiveAgent.retrieve(anyString(), anyString(), any())).thenReturn(noEvidence);

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("question", inquiryId);

        assertThat(result.isSingleHop()).isTrue();
        assertThat(result.evidences()).isEmpty();
    }

    private RerankingService.RerankResult rerankResult(double score) {
        return new RerankingService.RerankResult(UUID.randomUUID(), UUID.randomUUID(), "content",
                score, score, "INQUIRY", "VECTOR");
    }
}
