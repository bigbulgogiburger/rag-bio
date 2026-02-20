package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorizingServiceTest {

    @Mock private DocumentChunkJpaRepository chunkRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;

    private VectorizingService service;

    @BeforeEach
    void setUp() {
        service = new VectorizingService(chunkRepository, embeddingService, vectorStore);
    }

    @Test
    void upsertDocumentChunks_noChunks_returnsZero() {
        UUID docId = UUID.randomUUID();
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of());

        int result = service.upsertDocumentChunks(docId);

        assertThat(result).isEqualTo(0);
        verify(vectorStore, never()).upsert(any(), any(), anyList(), anyString(), anyString());
    }

    @Test
    void upsertDocumentChunks_withChunks_embedsAndUpserts() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk1 = mock(DocumentChunkJpaEntity.class);
        DocumentChunkJpaEntity chunk2 = mock(DocumentChunkJpaEntity.class);
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();

        when(chunk1.getId()).thenReturn(chunkId1);
        when(chunk1.getContent()).thenReturn("content 1");
        when(chunk1.getSourceType()).thenReturn("INQUIRY");
        when(chunk2.getId()).thenReturn(chunkId2);
        when(chunk2.getContent()).thenReturn("content 2");
        when(chunk2.getSourceType()).thenReturn("KNOWLEDGE_BASE");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk1, chunk2));
        when(embeddingService.embed("content 1")).thenReturn(List.of(0.1, 0.2));
        when(embeddingService.embed("content 2")).thenReturn(List.of(0.3, 0.4));

        int result = service.upsertDocumentChunks(docId);

        assertThat(result).isEqualTo(2);
        verify(vectorStore).upsert(chunkId1, docId, List.of(0.1, 0.2), "content 1", "INQUIRY");
        verify(vectorStore).upsert(chunkId2, docId, List.of(0.3, 0.4), "content 2", "KNOWLEDGE_BASE");
    }

    @Test
    void upsertDocumentChunks_nullSourceType_defaultsToInquiry() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        UUID chunkId = UUID.randomUUID();

        when(chunk.getId()).thenReturn(chunkId);
        when(chunk.getContent()).thenReturn("content");
        when(chunk.getSourceType()).thenReturn(null);

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk));
        when(embeddingService.embed("content")).thenReturn(List.of(0.5));

        service.upsertDocumentChunks(docId);

        verify(vectorStore).upsert(chunkId, docId, List.of(0.5), "content", "INQUIRY");
    }
}
