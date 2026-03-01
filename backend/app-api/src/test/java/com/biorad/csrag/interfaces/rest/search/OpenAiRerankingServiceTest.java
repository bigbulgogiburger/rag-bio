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
        service = new OpenAiRerankingService(restClient, "gpt-5.2", new ObjectMapper(), fallback);
    }

    @Test
    void rerank_validResponse_parsesScore() {
        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"score\\": 0.85, \\"reason\\": \\"directly relevant\\"}"}}]}
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
                {"choices":[{"message":{"content":"```json\\n{\\"score\\": 0.72, \\"reason\\": \\"partially relevant\\"}\\n```"}}]}
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
        when(responseSpec.body(String.class))
                .thenReturn("""
                        {"choices":[{"message":{"content":"{\\"score\\": 0.3, \\"reason\\": \\"low\\"}"}}]}
                        """)
                .thenReturn("""
                        {"choices":[{"message":{"content":"{\\"score\\": 0.95, \\"reason\\": \\"high\\"}"}}]}
                        """)
                .thenReturn("""
                        {"choices":[{"message":{"content":"{\\"score\\": 0.6, \\"reason\\": \\"mid\\"}"}}]}
                        """);

        List<HybridSearchResult> candidates = List.of(candidate(0.5), candidate(0.5), candidate(0.5));
        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 2);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.95);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.6);
    }

    @Test
    void rerank_longContent_truncated() {
        String longContent = "A".repeat(2000);
        HybridSearchResult candidate = new HybridSearchResult(
                UUID.randomUUID(), UUID.randomUUID(), longContent,
                0.8, 0.5, 0.65, "INQUIRY", "VECTOR"
        );

        String apiResponse = """
                {"choices":[{"message":{"content":"{\\"score\\": 0.7, \\"reason\\": \\"relevant\\"}"}}]}
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

    private HybridSearchResult candidate(double fusedScore) {
        return new HybridSearchResult(
                UUID.randomUUID(), UUID.randomUUID(), "sample content",
                fusedScore * 0.8, fusedScore * 0.6, fusedScore,
                "INQUIRY", "HYBRID"
        );
    }
}
