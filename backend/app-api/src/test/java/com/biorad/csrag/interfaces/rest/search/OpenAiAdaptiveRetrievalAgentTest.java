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

class OpenAiAdaptiveRetrievalAgentTest {

    private HybridSearchService hybridSearchService;
    private RerankingService rerankingService;
    private RestClient restClient;
    private RestClient.ResponseSpec responseSpec;
    private OpenAiAdaptiveRetrievalAgent agent;

    @BeforeEach
    void setUp() {
        hybridSearchService = mock(HybridSearchService.class);
        rerankingService = mock(RerankingService.class);

        restClient = mock(RestClient.class);
        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        agent = new OpenAiAdaptiveRetrievalAgent(
                hybridSearchService, rerankingService, restClient, new ObjectMapper(), "gpt-5.2");
    }

    @Test
    void retrieve_highScoreFirstAttempt_returnsSuccessWithoutReformulation() {
        UUID inquiryId = UUID.randomUUID();
        List<HybridSearchResult> candidates = List.of(candidate(0.9));
        List<RerankingService.RerankResult> results = List.of(rerankResult(0.9));

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(candidates);
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(results);

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("AAV serotype 호환성", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.SUCCESS);
        assertThat(result.attempts()).isEqualTo(1);
        // 첫 시도 성공이면 LLM reformulation 없음
        verify(restClient, never()).post();
    }

    @Test
    void retrieve_lowScore_reformulatesAndRetries() {
        UUID inquiryId = UUID.randomUUID();

        // 첫 번째 검색: 낮은 점수
        when(hybridSearchService.search(anyString(), anyInt(), any()))
                .thenReturn(List.of(candidate(0.3)))
                .thenReturn(List.of(candidate(0.8)));
        when(rerankingService.rerank(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(rerankResult(0.3)))
                .thenReturn(List.of(rerankResult(0.8)));

        // LLM reformulation 응답
        String reformResponse = """
                {"choices":[{"message":{"content":"AAV compatibility ddPCR"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(reformResponse);

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("AAV 호환성 문의", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.SUCCESS);
        verify(restClient, atLeastOnce()).post(); // reformulation 발생
    }

    @Test
    void retrieve_allAttemptsLowScore_returnsLowConfidence() {
        UUID inquiryId = UUID.randomUUID();

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(List.of(candidate(0.2)));
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of(rerankResult(0.2)));
        when(responseSpec.body(String.class)).thenReturn(
                """
                {"choices":[{"message":{"content":"reformulated query"}}]}
                """);

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("very obscure question", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.LOW_CONFIDENCE);
        assertThat(result.confidence()).isLessThan(0.5);
    }

    @Test
    void retrieve_emptyResults_returnsNoEvidence() {
        UUID inquiryId = UUID.randomUUID();

        when(hybridSearchService.search(anyString(), anyInt(), any())).thenReturn(List.of());
        when(rerankingService.rerank(anyString(), anyList(), anyInt())).thenReturn(List.of());
        when(responseSpec.body(String.class)).thenReturn(
                """
                {"choices":[{"message":{"content":"expanded query"}}]}
                """);

        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("no match query", "", inquiryId);

        assertThat(result.status()).isEqualTo(AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.NO_EVIDENCE);
        assertThat(result.evidences()).isEmpty();
    }

    @Test
    void retrieve_reformulationFails_continuesWithOriginalQuery() {
        UUID inquiryId = UUID.randomUUID();

        when(hybridSearchService.search(anyString(), anyInt(), any()))
                .thenReturn(List.of(candidate(0.3)))
                .thenReturn(List.of(candidate(0.6)));
        when(rerankingService.rerank(anyString(), anyList(), anyInt()))
                .thenReturn(List.of(rerankResult(0.3)))
                .thenReturn(List.of(rerankResult(0.6)));

        // LLM reformulation 실패
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("API error"));

        // 실패해도 예외 던지지 않고 최선의 결과 반환
        AdaptiveRetrievalAgent.AdaptiveResult result = agent.retrieve("question", "", inquiryId);

        assertThat(result).isNotNull();
        assertThat(result.status()).isIn(
                AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.SUCCESS,
                AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.LOW_CONFIDENCE,
                AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.NO_EVIDENCE);
    }

    private HybridSearchResult candidate(double score) {
        return new HybridSearchResult(UUID.randomUUID(), UUID.randomUUID(), "sample content",
                score, 0.0, score, "INQUIRY", "VECTOR");
    }

    private RerankingService.RerankResult rerankResult(double score) {
        return new RerankingService.RerankResult(UUID.randomUUID(), UUID.randomUUID(), "sample content",
                score, score, "INQUIRY", "VECTOR");
    }
}
