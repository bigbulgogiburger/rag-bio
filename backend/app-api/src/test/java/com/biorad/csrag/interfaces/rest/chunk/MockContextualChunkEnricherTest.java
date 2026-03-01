package com.biorad.csrag.interfaces.rest.chunk;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.*;

class MockContextualChunkEnricherTest {

    private final MockContextualChunkEnricher enricher = new MockContextualChunkEnricher();

    @Test
    void enrichChunks_setsEnrichedContentToContentWhenNull() {
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        when(chunk.getEnrichedContent()).thenReturn(null);
        when(chunk.getContent()).thenReturn("some content");

        enricher.enrichChunks("full doc text", List.of(chunk), "test.pdf");

        verify(chunk).setEnrichedContent("some content");
    }

    @Test
    void enrichChunks_doesNotOverwriteExistingEnrichedContent() {
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        when(chunk.getEnrichedContent()).thenReturn("already enriched");

        enricher.enrichChunks("full doc text", List.of(chunk), "test.pdf");

        verify(chunk, never()).setEnrichedContent(anyString());
    }

    @Test
    void enrichChunks_handlesNullList() {
        // Should not throw
        enricher.enrichChunks("doc", null, "test.pdf");
    }

    @Test
    void enrichChunks_handlesEmptyList() {
        // Should not throw
        enricher.enrichChunks("doc", List.of(), "test.pdf");
    }
}
