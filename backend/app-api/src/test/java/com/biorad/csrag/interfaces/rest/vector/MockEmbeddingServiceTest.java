package com.biorad.csrag.interfaces.rest.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockEmbeddingServiceTest {

    private final MockEmbeddingService service = new MockEmbeddingService();

    @Test
    void embed_returnsDimension3072() {
        List<Double> vector = service.embed("test input");

        assertThat(vector).hasSize(3072);
    }

    @Test
    void embed_deterministicOutput() {
        List<Double> first = service.embed("same text");
        List<Double> second = service.embed("same text");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void embed_differentInputProducesDifferentOutput() {
        List<Double> a = service.embed("text A");
        List<Double> b = service.embed("text B");

        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void embed_nullInput_returnsValidVector() {
        List<Double> vector = service.embed(null);

        assertThat(vector).hasSize(3072);
    }

    @Test
    void embedBatch_returnsCorrectCount() {
        List<List<Double>> results = service.embedBatch(List.of("a", "b", "c"));

        assertThat(results).hasSize(3);
        results.forEach(v -> assertThat(v).hasSize(3072));
    }
}
