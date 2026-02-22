package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.interfaces.rest.search.SearchFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockVectorStoreTest {

    private MockVectorStore store;

    @BeforeEach
    void setUp() {
        store = new MockVectorStore();
    }

    @Test
    void upsert_and_search() {
        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(1.0, 0.0, 0.0);
        store.upsert(chunkId, docId, vector, "test content");

        List<VectorSearchResult> results = store.search(List.of(1.0, 0.0, 0.0), 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).chunkId()).isEqualTo(chunkId);
        assertThat(results.get(0).documentId()).isEqualTo(docId);
        assertThat(results.get(0).content()).isEqualTo("test content");
        assertThat(results.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void upsert_withSourceType() {
        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(0.5, 0.5, 0.0);
        store.upsert(chunkId, docId, vector, "kb content", "KNOWLEDGE_BASE");

        List<VectorSearchResult> results = store.search(List.of(0.5, 0.5, 0.0), 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).sourceType()).isEqualTo("KNOWLEDGE_BASE");
    }

    @Test
    void search_returnsTopKSortedByScore() {
        UUID docId = UUID.randomUUID();
        store.upsert(UUID.randomUUID(), docId, List.of(1.0, 0.0, 0.0), "c1");
        store.upsert(UUID.randomUUID(), docId, List.of(0.0, 1.0, 0.0), "c2");
        store.upsert(UUID.randomUUID(), docId, List.of(0.5, 0.5, 0.0), "c3");

        List<VectorSearchResult> results = store.search(List.of(1.0, 0.0, 0.0), 2);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
    }

    @Test
    void search_emptyStore_returnsEmpty() {
        List<VectorSearchResult> results = store.search(List.of(1.0, 0.0), 5);
        assertThat(results).isEmpty();
    }

    @Test
    void search_emptyVectors_returnsZeroScore() {
        UUID chunkId = UUID.randomUUID();
        store.upsert(chunkId, UUID.randomUUID(), List.of(), "empty");
        List<VectorSearchResult> results = store.search(List.of(), 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).score()).isEqualTo(0.0);
    }

    @Test
    void deleteByDocumentId_removesAllChunksForDocument() {
        UUID docId = UUID.randomUUID();
        store.upsert(UUID.randomUUID(), docId, List.of(1.0), "c1");
        store.upsert(UUID.randomUUID(), docId, List.of(0.5), "c2");
        store.upsert(UUID.randomUUID(), UUID.randomUUID(), List.of(0.3), "c3");

        assertThat(store.size()).isEqualTo(3);
        store.deleteByDocumentId(docId);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void size_returnsCount() {
        assertThat(store.size()).isEqualTo(0);
        store.upsert(UUID.randomUUID(), UUID.randomUUID(), List.of(1.0), "c1");
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        UUID docId = UUID.randomUUID();
        store.upsert(UUID.randomUUID(), docId, List.of(1.0, 0.0), "c1");
        List<VectorSearchResult> results = store.search(List.of(0.0, 1.0), 5);
        assertThat(results.get(0).score()).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void upsert_overwritesSameChunkId() {
        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        store.upsert(chunkId, docId, List.of(1.0), "original");
        store.upsert(chunkId, docId, List.of(1.0), "updated");
        assertThat(store.size()).isEqualTo(1);
        List<VectorSearchResult> results = store.search(List.of(1.0), 5);
        assertThat(results.get(0).content()).isEqualTo("updated");
    }

    @Test
    void search_withMultipleProductFamilies_matchesAny() {
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(1.0, 0.0, 0.0);

        store.upsert(UUID.randomUUID(), docId, vector, "naica doc", "KNOWLEDGE_BASE", "naica");
        store.upsert(UUID.randomUUID(), docId, vector, "cfx doc", "KNOWLEDGE_BASE", "CFX96");
        store.upsert(UUID.randomUUID(), docId, vector, "qx doc", "KNOWLEDGE_BASE", "QX200");

        SearchFilter filter = SearchFilter.forProducts(null, Set.of("naica", "CFX96"));
        List<VectorSearchResult> results = store.search(vector, 10, filter);

        assertThat(results).hasSize(2);
        assertThat(results).extracting(VectorSearchResult::content)
                .containsExactlyInAnyOrder("naica doc", "cfx doc");
    }

    @Test
    void search_withMultipleProductFamilies_excludesOthers() {
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(1.0, 0.0, 0.0);

        store.upsert(UUID.randomUUID(), docId, vector, "naica doc", "KNOWLEDGE_BASE", "naica");
        store.upsert(UUID.randomUUID(), docId, vector, "other doc", "KNOWLEDGE_BASE", "BioPlex2200");

        SearchFilter filter = SearchFilter.forProducts(null, Set.of("naica"));
        List<VectorSearchResult> results = store.search(vector, 10, filter);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).content()).isEqualTo("naica doc");
    }

    @Test
    void search_withProductFilter_caseInsensitive() {
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(1.0, 0.0, 0.0);

        store.upsert(UUID.randomUUID(), docId, vector, "naica doc", "KNOWLEDGE_BASE", "naica");

        SearchFilter filter = SearchFilter.forProducts(null, Set.of("NAICA"));
        List<VectorSearchResult> results = store.search(vector, 10, filter);

        assertThat(results).hasSize(1);
    }

    @Test
    void search_withNullFilter_returnsAll() {
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(1.0, 0.0, 0.0);

        store.upsert(UUID.randomUUID(), docId, vector, "c1", "INQUIRY", null);
        store.upsert(UUID.randomUUID(), docId, vector, "c2", "KNOWLEDGE_BASE", "naica");

        List<VectorSearchResult> results = store.search(vector, 10, null);
        assertThat(results).hasSize(2);
    }

    @Test
    void upsert_withProductFamily_storesMetadata() {
        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        List<Double> vector = List.of(1.0, 0.0);

        store.upsert(chunkId, docId, vector, "content", "KNOWLEDGE_BASE", "naica");

        assertThat(store.size()).isEqualTo(1);

        // Search without filter returns the record
        List<VectorSearchResult> results = store.search(vector, 5);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).sourceType()).isEqualTo("KNOWLEDGE_BASE");
    }
}
