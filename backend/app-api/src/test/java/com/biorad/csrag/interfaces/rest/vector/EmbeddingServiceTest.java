package com.biorad.csrag.interfaces.rest.vector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingServiceTest {

    private final EmbeddingService service = text -> List.of(1.0, 2.0, 3.0);

    @Test
    void embedDocument_delegatesToEmbed() {
        List<Double> result = service.embedDocument("hello");

        assertThat(result).isEqualTo(List.of(1.0, 2.0, 3.0));
    }

    @Test
    void embedQuery_delegatesToEmbed() {
        List<Double> result = service.embedQuery("hello");

        assertThat(result).isEqualTo(List.of(1.0, 2.0, 3.0));
    }

    @Test
    void embedBatch_processesSequentially() {
        List<List<Double>> result = service.embedBatch(List.of("a", "b", "c"));

        assertThat(result).hasSize(3);
        assertThat(result.get(0)).isEqualTo(List.of(1.0, 2.0, 3.0));
        assertThat(result.get(1)).isEqualTo(List.of(1.0, 2.0, 3.0));
        assertThat(result.get(2)).isEqualTo(List.of(1.0, 2.0, 3.0));
    }

    @Test
    void embedBatch_emptyList_returnsEmpty() {
        List<List<Double>> result = service.embedBatch(List.of());

        assertThat(result).isEmpty();
    }
}
