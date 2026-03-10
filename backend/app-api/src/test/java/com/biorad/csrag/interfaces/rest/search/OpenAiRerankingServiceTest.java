package com.biorad.csrag.interfaces.rest.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.Mockito.*;

class OpenAiRerankingServiceTest {

    private RestClient restClient;
    private RestClient.ResponseSpec responseSpec;
    private MockRerankingService fallback;
    private OpenAiRerankingService service;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class);
        var requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        var requestBodySpec = mock(RestClient.RequestBodySpec.class, RETURNS_SELF);
        responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(anyString())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);

        fallback = new MockRerankingService();
        service = new OpenAiRerankingService(restClient, "gpt-5.2", new ObjectMapper(), fallback, null);
    }

    @Test
    void rerank_validResponse_parsesScores() {
        String apiResponse = """
                {"choices":[{"message":{"content":"[{\\"index\\": 0, \\"score\\": 0.85}]"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<RerankingService.RerankResult> results = service.rerank("query", List.of(candidate(0.5)), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.85);
        assertThat(results.get(0).originalScore()).isEqualTo(0.5);
    }

    @Test
    void rerank_codeFencedResponse_parsesCorrectly() {
        String apiResponse = """
                {"choices":[{"message":{"content":"```json\\n[{\\"index\\": 0, \\"score\\": 0.72}]\\n```"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<RerankingService.RerankResult> results = service.rerank("query", List.of(candidate(0.4)), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.72);
    }

    @Test
    void rerank_apiError_fallsBackToMock() {
        when(responseSpec.body(String.class)).thenThrow(new RuntimeException("API unavailable"));

        List<HybridSearchResult> candidates = List.of(candidate(0.9), candidate(0.3));
        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.9);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.3);
    }

    @Test
    void rerank_multipleCandidates_sortedByRerankScore() {
        // Listwise: single call returns all scores
        String apiResponse = """
                {"choices":[{"message":{"content":"[{\\"index\\": 0, \\"score\\": 0.3}, {\\"index\\": 1, \\"score\\": 0.95}, {\\"index\\": 2, \\"score\\": 0.6}]"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<HybridSearchResult> candidates = List.of(candidate(0.5), candidate(0.5), candidate(0.5));
        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.95);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.6);
        // Only 1 API call (listwise), not 3 (pointwise)
        verify(restClient, times(1)).post();
    }

    @Test
    void rerank_longContent_truncated() {
        String longContent = "A".repeat(2000);
        HybridSearchResult candidate = new HybridSearchResult(
                UUID.randomUUID(), UUID.randomUUID(), longContent,
                0.8, 0.5, 0.65, "INQUIRY", "VECTOR"
        );

        String apiResponse = """
                {"choices":[{"message":{"content":"[{\\"index\\": 0, \\"score\\": 0.7}]"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<RerankingService.RerankResult> results = service.rerank("query", List.of(candidate), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.7);
        verify(restClient, times(1)).post();
    }

    @Test
    void rerank_emptyCandidates_returnsEmpty() {
        List<RerankingService.RerankResult> results = service.rerank("query", List.of(), 10);

        assertThat(results).isEmpty();
        verify(restClient, never()).post();
    }

    @Test
    void rerank_objectWithRankingsKey_parsesCorrectly() {
        // Some LLMs wrap the array in an object with "rankings" key
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"rankings\\": [{\\"index\\": 0, \\"score\\": 0.88}]}"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<RerankingService.RerankResult> results = service.rerank("query", List.of(candidate(0.5)), 1);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.88);
    }

    @Test
    void rerank_missingIndex_fallsBackToFusedScore() {
        // Response only includes index 0 but not index 1 — index 1 should fall back to fusedScore
        String apiResponse = """
                {"choices":[{"message":{"content":"[{\\"index\\": 0, \\"score\\": 0.9}]"}}]}
                """;
        when(responseSpec.body(String.class)).thenReturn(apiResponse);

        List<HybridSearchResult> candidates = List.of(candidate(0.5), candidate(0.7));
        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 2);

        assertThat(results).hasSize(2);
        // First by score: 0.9 (index 0), then 0.7 (index 1 falls back to fusedScore)
        assertThat(results.get(0).rerankScore()).isEqualTo(0.9);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.7);
    }

    private HybridSearchResult candidate(double fusedScore) {
        return new HybridSearchResult(
                UUID.randomUUID(), UUID.randomUUID(), "sample content",
                fusedScore * 0.8, fusedScore * 0.6, fusedScore,
                "INQUIRY", "HYBRID"
        );
    }
}
