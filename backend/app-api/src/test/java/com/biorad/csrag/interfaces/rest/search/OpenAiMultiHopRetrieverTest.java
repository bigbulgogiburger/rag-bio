package com.biorad.csrag.interfaces.rest.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OpenAiMultiHopRetrieverTest {

    private AdaptiveRetrievalAgent adaptiveAgent;
    private RestClient restClient;
    private RestClient.ResponseSpec responseSpec;
    private OpenAiMultiHopRetriever retriever;

    @BeforeEach
    void setUp() {
        adaptiveAgent = mock(AdaptiveRetrievalAgent.class);
        restClient = mock(RestClient.class);
        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        retriever = new OpenAiMultiHopRetriever(adaptiveAgent, restClient, "gpt-5.2", new ObjectMapper(), null);
    }

    @Test
    void retrieve_singleHopSufficient_noSecondHop() {
        UUID inquiryId = UUID.randomUUID();
        List<RerankingService.RerankResult> evidences = List.of(rerankResult(0.9));
        AdaptiveRetrievalAgent.AdaptiveResult hop1 = AdaptiveRetrievalAgent.AdaptiveResult.success(evidences, 1);

        when(adaptiveAgent.retrieve(anyString(), anyString(), any())).thenReturn(hop1);

        // LLM says no more hops needed
        when(responseSpec.body(String.class)).thenReturn(
                """
                {"choices":[{"message":{"content":"{\\"needs_more_hops\\": false}"}}]}
                """);

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("simple question", inquiryId);

        assertThat(result.isSingleHop()).isTrue();
        assertThat(result.evidences()).hasSize(1);
        // AdaptiveAgent 1번만 호출
        verify(adaptiveAgent, times(1)).retrieve(anyString(), anyString(), any());
    }

    @Test
    void retrieve_multiHopNeeded_performsSecondHop() {
        UUID inquiryId = UUID.randomUUID();
        List<RerankingService.RerankResult> hop1Evidences = List.of(rerankResult(0.7));
        List<RerankingService.RerankResult> hop2Evidences = List.of(rerankResult(0.8));

        AdaptiveRetrievalAgent.AdaptiveResult hop1Result = AdaptiveRetrievalAgent.AdaptiveResult.success(hop1Evidences, 1);
        AdaptiveRetrievalAgent.AdaptiveResult hop2Result = AdaptiveRetrievalAgent.AdaptiveResult.success(hop2Evidences, 1);

        when(adaptiveAgent.retrieve(anyString(), anyString(), any()))
                .thenReturn(hop1Result)
                .thenReturn(hop2Result);

        // LLM says needs more hops
        when(responseSpec.body(String.class)).thenReturn(
                """
                {"choices":[{"message":{"content":"{\\"needs_more_hops\\": true, \\"next_query\\": \\"naica ddPCR Supermix compatibility\\"}"}}]}
                """);

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("QX700 + naica 호환성", inquiryId);

        assertThat(result.isSingleHop()).isFalse();
        assertThat(result.hops()).hasSize(2);
        assertThat(result.evidences()).hasSize(2); // hop1 + hop2 병합
        verify(adaptiveAgent, times(2)).retrieve(anyString(), anyString(), eq(inquiryId));
    }

    @Test
    void retrieve_deduplicatesEvidences() {
        UUID inquiryId = UUID.randomUUID();
        UUID sharedChunkId = UUID.randomUUID();

        RerankingService.RerankResult shared = new RerankingService.RerankResult(
                sharedChunkId, UUID.randomUUID(), "shared content", 0.8, 0.8, "INQUIRY", "VECTOR");
        RerankingService.RerankResult unique = rerankResult(0.7);

        AdaptiveRetrievalAgent.AdaptiveResult hop1 = AdaptiveRetrievalAgent.AdaptiveResult.success(List.of(shared), 1);
        AdaptiveRetrievalAgent.AdaptiveResult hop2 = AdaptiveRetrievalAgent.AdaptiveResult.success(List.of(shared, unique), 1);

        when(adaptiveAgent.retrieve(anyString(), anyString(), any()))
                .thenReturn(hop1)
                .thenReturn(hop2);

        when(responseSpec.body(String.class)).thenReturn(
                """
                {"choices":[{"message":{"content":"{\\"needs_more_hops\\": true, \\"next_query\\": \\"follow up query\\"}"}}]}
                """);

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("complex question", inquiryId);

        // shared chunk은 한 번만 포함 (중복 제거)
        assertThat(result.evidences()).hasSize(2);
        long sharedCount = result.evidences().stream()
                .filter(e -> e.chunkId().equals(sharedChunkId)).count();
        assertThat(sharedCount).isEqualTo(1);
    }

    @Test
    void retrieve_emptyHop1Results_returnsSingleHop() {
        UUID inquiryId = UUID.randomUUID();
        AdaptiveRetrievalAgent.AdaptiveResult noEvidence =
                AdaptiveRetrievalAgent.AdaptiveResult.noEvidence("question");

        when(adaptiveAgent.retrieve(anyString(), anyString(), any())).thenReturn(noEvidence);

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("question", inquiryId);

        assertThat(result.isSingleHop()).isTrue();
        assertThat(result.evidences()).isEmpty();
        // hop evaluation 스킵 (empty results)
        verify(restClient, never()).post();
    }

    @Test
    void retrieve_llmEvaluationFails_fallsBackToSingleHop() {
        UUID inquiryId = UUID.randomUUID();
        List<RerankingService.RerankResult> evidences = List.of(rerankResult(0.8));
        AdaptiveRetrievalAgent.AdaptiveResult hop1 = AdaptiveRetrievalAgent.AdaptiveResult.success(evidences, 1);

        when(adaptiveAgent.retrieve(anyString(), anyString(), any())).thenReturn(hop1);
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("API error"));

        MultiHopRetriever.MultiHopResult result = retriever.retrieve("question", inquiryId);

        // 실패해도 hop1 결과 반환
        assertThat(result.isSingleHop()).isTrue();
        assertThat(result.evidences()).hasSize(1);
    }

    private RerankingService.RerankResult rerankResult(double score) {
        return new RerankingService.RerankResult(UUID.randomUUID(), UUID.randomUUID(), "content",
                score, score, "INQUIRY", "VECTOR");
    }
}
