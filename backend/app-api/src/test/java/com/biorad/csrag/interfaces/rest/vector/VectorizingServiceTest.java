package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ContextualChunkEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorizingServiceTest {

    @Mock private DocumentChunkJpaRepository chunkRepository;
    @Mock private KnowledgeDocumentJpaRepository kbDocRepository;
    @Mock private EmbeddingService embeddingService;
    @Mock private VectorStore vectorStore;
    @Mock private ContextualChunkEnricher contextualChunkEnricher;

    private VectorizingService service;

    @BeforeEach
    void setUp() {
        service = new VectorizingService(chunkRepository, kbDocRepository, embeddingService, vectorStore, contextualChunkEnricher, null);
    }

    @Test
    void upsertDocumentChunks_noChunks_returnsZero() {
        UUID docId = UUID.randomUUID();
        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(List.of());

        int result = service.upsertDocumentChunks(docId);

        assertThat(result).isEqualTo(0);
        verify(vectorStore, never()).upsert(any(), any(), anyList(), anyString(), anyString(), any());
    }

    @Test
    void upsertDocumentChunks_kbChunks_resolvesProductFamilyFromParent() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk1 = mock(DocumentChunkJpaEntity.class);
        DocumentChunkJpaEntity chunk2 = mock(DocumentChunkJpaEntity.class);
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();

        when(chunk1.getId()).thenReturn(chunkId1);
        when(chunk1.getContent()).thenReturn("content 1");
        when(chunk1.getSourceType()).thenReturn("KNOWLEDGE_BASE");
        when(chunk1.getChunkLevel()).thenReturn(null);
        when(chunk1.getEnrichedContent()).thenReturn(null);
        when(chunk2.getId()).thenReturn(chunkId2);
        when(chunk2.getContent()).thenReturn("content 2");
        when(chunk2.getSourceType()).thenReturn("KNOWLEDGE_BASE");
        when(chunk2.getChunkLevel()).thenReturn(null);
        when(chunk2.getEnrichedContent()).thenReturn(null);

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk1, chunk2));

        // KB 부모 문서에서 productFamily 조회
        KnowledgeDocumentJpaEntity kbDoc = mock(KnowledgeDocumentJpaEntity.class);
        when(kbDoc.getProductFamily()).thenReturn("naica");
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(kbDoc));

        when(embeddingService.embedBatch(List.of("content 1", "content 2")))
                .thenReturn(List.of(List.of(0.1, 0.2), List.of(0.3, 0.4)));

        int result = service.upsertDocumentChunks(docId);

        assertThat(result).isEqualTo(2);
        verify(vectorStore).upsert(chunkId1, docId, List.of(0.1, 0.2), "content 1", "KNOWLEDGE_BASE", "naica");
        verify(vectorStore).upsert(chunkId2, docId, List.of(0.3, 0.4), "content 2", "KNOWLEDGE_BASE", "naica");
        verify(contextualChunkEnricher).enrichChunks(eq(""), anyList(), eq(""));
    }

    @Test
    void upsertDocumentChunks_inquiryChunks_productFamilyNull() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        UUID chunkId = UUID.randomUUID();

        when(chunk.getId()).thenReturn(chunkId);
        when(chunk.getContent()).thenReturn("content");
        when(chunk.getSourceType()).thenReturn("INQUIRY");
        when(chunk.getProductFamily()).thenReturn(null);
        when(chunk.getChunkLevel()).thenReturn(null);
        when(chunk.getEnrichedContent()).thenReturn(null);

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk));
        when(embeddingService.embedBatch(List.of("content")))
                .thenReturn(List.of(List.of(0.5)));

        service.upsertDocumentChunks(docId);

        verify(vectorStore).upsert(chunkId, docId, List.of(0.5), "content", "INQUIRY", null);
        verify(kbDocRepository, never()).findById(any());
    }

    @Test
    void upsertDocumentChunks_nullSourceType_defaultsToInquiry() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        UUID chunkId = UUID.randomUUID();

        when(chunk.getId()).thenReturn(chunkId);
        when(chunk.getContent()).thenReturn("content");
        when(chunk.getSourceType()).thenReturn(null);
        when(chunk.getProductFamily()).thenReturn(null);
        when(chunk.getChunkLevel()).thenReturn(null);
        when(chunk.getEnrichedContent()).thenReturn(null);

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk));
        when(embeddingService.embedBatch(List.of("content")))
                .thenReturn(List.of(List.of(0.5)));

        service.upsertDocumentChunks(docId);

        verify(vectorStore).upsert(chunkId, docId, List.of(0.5), "content", "INQUIRY", null);
    }

    @Test
    void upsertDocumentChunks_usesEmbedBatchInsteadOfEmbed() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        UUID chunkId = UUID.randomUUID();

        when(chunk.getId()).thenReturn(chunkId);
        when(chunk.getContent()).thenReturn("content");
        when(chunk.getSourceType()).thenReturn("INQUIRY");
        when(chunk.getProductFamily()).thenReturn(null);
        when(chunk.getChunkLevel()).thenReturn(null);
        when(chunk.getEnrichedContent()).thenReturn(null);

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk));
        when(embeddingService.embedBatch(List.of("content")))
                .thenReturn(List.of(List.of(0.5)));

        service.upsertDocumentChunks(docId);

        // embedBatch should be called, NOT embed
        verify(embeddingService).embedBatch(List.of("content"));
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void upsertDocumentChunks_batchesInGroupsOf50() {
        UUID docId = UUID.randomUUID();
        List<DocumentChunkJpaEntity> chunks = new ArrayList<>();
        for (int i = 0; i < 75; i++) {
            DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
            when(chunk.getId()).thenReturn(UUID.randomUUID());
            when(chunk.getContent()).thenReturn("content " + i);
            when(chunk.getSourceType()).thenReturn("INQUIRY");
            when(chunk.getProductFamily()).thenReturn(null);
            when(chunk.getChunkLevel()).thenReturn(null);
            when(chunk.getEnrichedContent()).thenReturn(null);
            chunks.add(chunk);
        }

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId)).thenReturn(chunks);

        // First batch: 50 items
        List<String> firstBatchTexts = new ArrayList<>();
        for (int i = 0; i < 50; i++) firstBatchTexts.add("content " + i);
        List<List<Double>> firstBatchVectors = new ArrayList<>();
        for (int i = 0; i < 50; i++) firstBatchVectors.add(List.of((double) i));
        when(embeddingService.embedBatch(firstBatchTexts)).thenReturn(firstBatchVectors);

        // Second batch: 25 items
        List<String> secondBatchTexts = new ArrayList<>();
        for (int i = 50; i < 75; i++) secondBatchTexts.add("content " + i);
        List<List<Double>> secondBatchVectors = new ArrayList<>();
        for (int i = 50; i < 75; i++) secondBatchVectors.add(List.of((double) i));
        when(embeddingService.embedBatch(secondBatchTexts)).thenReturn(secondBatchVectors);

        int result = service.upsertDocumentChunks(docId);

        assertThat(result).isEqualTo(75);
        // embedBatch called exactly twice (50 + 25)
        verify(embeddingService, times(2)).embedBatch(anyList());
        verify(vectorStore, times(75)).upsert(any(), eq(docId), anyList(), anyString(), anyString(), any());
    }

    @Test
    void upsertDocumentChunks_parentChunksExcludedFromEmbedding() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity parentChunk = mock(DocumentChunkJpaEntity.class);
        DocumentChunkJpaEntity childChunk = mock(DocumentChunkJpaEntity.class);
        UUID parentId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();

        when(parentChunk.getChunkLevel()).thenReturn("PARENT");
        when(parentChunk.getSourceType()).thenReturn("INQUIRY");

        when(childChunk.getId()).thenReturn(childId);
        when(childChunk.getContent()).thenReturn("child content");
        when(childChunk.getChunkLevel()).thenReturn("CHILD");
        when(childChunk.getSourceType()).thenReturn("INQUIRY");
        when(childChunk.getProductFamily()).thenReturn(null);
        when(childChunk.getEnrichedContent()).thenReturn("context prefix\nchild content");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(parentChunk, childChunk));
        when(embeddingService.embedBatch(List.of("context prefix\nchild content")))
                .thenReturn(List.of(List.of(0.7)));

        int result = service.upsertDocumentChunks(docId);

        assertThat(result).isEqualTo(2);
        // Only child chunk should be embedded (parent excluded)
        verify(vectorStore).upsert(childId, docId, List.of(0.7), "child content", "INQUIRY", null);
        verify(vectorStore, never()).upsert(eq(parentId), any(), anyList(), anyString(), anyString(), any());
    }

    @Test
    void upsertDocumentChunks_usesEnrichedContentForEmbedding() {
        UUID docId = UUID.randomUUID();
        DocumentChunkJpaEntity chunk = mock(DocumentChunkJpaEntity.class);
        UUID chunkId = UUID.randomUUID();

        when(chunk.getId()).thenReturn(chunkId);
        when(chunk.getContent()).thenReturn("original content");
        when(chunk.getSourceType()).thenReturn("INQUIRY");
        when(chunk.getProductFamily()).thenReturn(null);
        when(chunk.getChunkLevel()).thenReturn(null);
        when(chunk.getEnrichedContent()).thenReturn("context: enriched content");

        when(chunkRepository.findByDocumentIdOrderByChunkIndexAsc(docId))
                .thenReturn(List.of(chunk));
        when(embeddingService.embedBatch(List.of("context: enriched content")))
                .thenReturn(List.of(List.of(0.9)));

        service.upsertDocumentChunks(docId);

        // Embedding uses enrichedContent, but vector store stores original content
        verify(embeddingService).embedBatch(List.of("context: enriched content"));
        verify(vectorStore).upsert(chunkId, docId, List.of(0.9), "original content", "INQUIRY", null);
    }
}
