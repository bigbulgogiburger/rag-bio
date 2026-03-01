package com.biorad.csrag.interfaces.rest.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockRerankingServiceTest {

    private MockRerankingService service;

    @BeforeEach
    void setUp() {
        service = new MockRerankingService();
    }

    @Test
    void rerank_returnsSortedByFusedScoreDescending() {
        List<HybridSearchResult> candidates = List.of(
                candidate(0.3),
                candidate(0.9),
                candidate(0.6),
                candidate(0.1),
                candidate(0.7)
        );

        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 3);

        assertThat(results).hasSize(3);
        assertThat(results.get(0).rerankScore()).isEqualTo(0.9);
        assertThat(results.get(1).rerankScore()).isEqualTo(0.7);
        assertThat(results.get(2).rerankScore()).isEqualTo(0.6);
    }

    @Test
    void rerank_rerankScoreEqualsFusedScore() {
        List<HybridSearchResult> candidates = List.of(candidate(0.42));

        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 5);

        assertThat(results).hasSize(1);
        RerankingService.RerankResult result = results.get(0);
        assertThat(result.rerankScore()).isEqualTo(0.42);
        assertThat(result.originalScore()).isEqualTo(0.42);
    }

    @Test
    void rerank_emptyCandidates_returnsEmpty() {
        List<RerankingService.RerankResult> results = service.rerank("query", List.of(), 10);

        assertThat(results).isEmpty();
    }

    @Test
    void rerank_topKLargerThanCandidates_returnsAll() {
        List<HybridSearchResult> candidates = List.of(candidate(0.5), candidate(0.8));

        List<RerankingService.RerankResult> results = service.rerank("query", candidates, 10);

        assertThat(results).hasSize(2);
    }

    @Test
    void rerank_preservesFieldValues() {
        UUID chunkId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        HybridSearchResult candidate = new HybridSearchResult(
                chunkId, documentId, "test content",
                0.8, 0.5, 0.65, "KNOWLEDGE_BASE", "VECTOR"
        );

        List<RerankingService.RerankResult> results = service.rerank("query", List.of(candidate), 1);

        assertThat(results).hasSize(1);
        RerankingService.RerankResult result = results.get(0);
        assertThat(result.chunkId()).isEqualTo(chunkId);
        assertThat(result.documentId()).isEqualTo(documentId);
        assertThat(result.content()).isEqualTo("test content");
        assertThat(result.sourceType()).isEqualTo("KNOWLEDGE_BASE");
        assertThat(result.matchSource()).isEqualTo("VECTOR");
    }

    private HybridSearchResult candidate(double fusedScore) {
        return new HybridSearchResult(
                UUID.randomUUID(), UUID.randomUUID(), "content",
                fusedScore * 0.8, fusedScore * 0.6, fusedScore,
                "INQUIRY", "HYBRID"
        );
    }
}
