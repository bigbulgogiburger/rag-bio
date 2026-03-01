package com.biorad.csrag.interfaces.rest.analysis;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaRepository;
import com.biorad.csrag.interfaces.rest.search.HybridSearchService;
import com.biorad.csrag.interfaces.rest.search.QueryTranslationService;
import com.biorad.csrag.interfaces.rest.search.RerankingService;
import com.biorad.csrag.interfaces.rest.search.SearchFilter;
import com.biorad.csrag.interfaces.rest.search.TranslatedQuery;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Parent-Child Retrieval 테스트")
class AnalysisServiceParentChildTest {

    @Mock EmbeddingService embeddingService;
    @Mock VectorStore vectorStore;
    @Mock RetrievalEvidenceJpaRepository evidenceRepository;
    @Mock DocumentChunkJpaRepository chunkRepository;
    @Mock DocumentMetadataJpaRepository documentRepository;
    @Mock KnowledgeDocumentJpaRepository kbDocRepository;
    @Mock QueryTranslationService queryTranslationService;
    @Mock HybridSearchService hybridSearchService;
    @Mock RerankingService rerankingService;

    @InjectMocks
    AnalysisService analysisService;

    private static final UUID INQUIRY_ID = UUID.randomUUID();

    @Test
    @DisplayName("CHILD 청크 매칭 시 PARENT 콘텐츠가 EvidenceItem excerpt에 사용됨")
    void childChunkMatched_parentContentUsedInEvidence() {
        UUID childChunkId = UUID.randomUUID();
        UUID parentChunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String childContent = "child chunk content - narrow";
        String parentContent = "parent chunk content - broader context with more details";

        // Child chunk entity
        DocumentChunkJpaEntity childChunk = new DocumentChunkJpaEntity(
                childChunkId, docId, 1, 0, 100, childContent, "INQUIRY", docId, 1, 2, Instant.now());
        childChunk.setChunkLevel("CHILD");
        childChunk.setParentChunkId(parentChunkId);

        // Parent chunk entity
        DocumentChunkJpaEntity parentChunk = new DocumentChunkJpaEntity(
                parentChunkId, docId, 0, 0, 200, parentContent, "INQUIRY", docId, 1, 2, Instant.now());
        parentChunk.setChunkLevel("PARENT");

        // Mock pipeline
        when(queryTranslationService.translate("test query"))
                .thenReturn(new TranslatedQuery("test query", "test query", false));
        when(hybridSearchService.search(eq("test query"), anyInt(), any(SearchFilter.class)))
                .thenReturn(List.of());
        RerankingService.RerankResult rerankResult = new RerankingService.RerankResult(
                childChunkId, docId, childContent, 0.7, 0.85, "INQUIRY", "VECTOR");
        when(rerankingService.rerank(eq("test query"), any(), eq(5)))
                .thenReturn(List.of(rerankResult));

        // First findAllById call returns the child chunk, second returns parent
        when(chunkRepository.findAllById(any()))
                .thenReturn(List.of(childChunk))    // first call: child chunk lookup
                .thenReturn(List.of(parentChunk));   // second call: parent chunk lookup

        when(documentRepository.findAllById(any())).thenReturn(List.of());
        when(kbDocRepository.findAllById(any())).thenReturn(List.of());

        List<EvidenceItem> results = analysisService.retrieve(INQUIRY_ID, "test query", 5);

        assertThat(results).hasSize(1);
        // Parent content should be used, not child content
        assertThat(results.get(0).excerpt()).isEqualTo(parentContent);
    }

    @Test
    @DisplayName("일반 청크 (PARENT 레벨 아닌 경우) → 원본 content 사용")
    void regularChunk_originalContentUsed() {
        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String content = "regular chunk content";

        // Regular chunk with no parent (PARENT level itself or null level)
        DocumentChunkJpaEntity chunk = new DocumentChunkJpaEntity(
                chunkId, docId, 0, 0, 100, content, "INQUIRY", docId, 1, 1, Instant.now());
        chunk.setChunkLevel("PARENT");

        when(queryTranslationService.translate("test query"))
                .thenReturn(new TranslatedQuery("test query", "test query", false));
        when(hybridSearchService.search(eq("test query"), anyInt(), any(SearchFilter.class)))
                .thenReturn(List.of());
        RerankingService.RerankResult rerankResult = new RerankingService.RerankResult(
                chunkId, docId, content, 0.7, 0.80, "INQUIRY", "VECTOR");
        when(rerankingService.rerank(eq("test query"), any(), eq(5)))
                .thenReturn(List.of(rerankResult));
        when(chunkRepository.findAllById(any())).thenReturn(List.of(chunk));
        when(documentRepository.findAllById(any())).thenReturn(List.of());
        when(kbDocRepository.findAllById(any())).thenReturn(List.of());

        List<EvidenceItem> results = analysisService.retrieve(INQUIRY_ID, "test query", 5);

        assertThat(results).hasSize(1);
        // Original content from search result should be used (no parent substitution)
        assertThat(results.get(0).excerpt()).isEqualTo(content);
    }

    @Test
    @DisplayName("CHILD 청크인데 PARENT를 찾지 못하면 → 원본 child content로 fallback")
    void childChunk_parentNotFound_fallbackToChildContent() {
        UUID childChunkId = UUID.randomUUID();
        UUID parentChunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String childContent = "child chunk content - orphaned";

        // Child chunk with parentChunkId pointing to non-existent parent
        DocumentChunkJpaEntity childChunk = new DocumentChunkJpaEntity(
                childChunkId, docId, 1, 0, 100, childContent, "INQUIRY", docId, 1, 2, Instant.now());
        childChunk.setChunkLevel("CHILD");
        childChunk.setParentChunkId(parentChunkId);

        when(queryTranslationService.translate("test query"))
                .thenReturn(new TranslatedQuery("test query", "test query", false));
        when(hybridSearchService.search(eq("test query"), anyInt(), any(SearchFilter.class)))
                .thenReturn(List.of());
        RerankingService.RerankResult rerankResult = new RerankingService.RerankResult(
                childChunkId, docId, childContent, 0.7, 0.90, "INQUIRY", "VECTOR");
        when(rerankingService.rerank(eq("test query"), any(), eq(5)))
                .thenReturn(List.of(rerankResult));

        // First call returns child, second call returns empty (parent not found)
        when(chunkRepository.findAllById(any()))
                .thenReturn(List.of(childChunk))
                .thenReturn(List.of());

        when(documentRepository.findAllById(any())).thenReturn(List.of());
        when(kbDocRepository.findAllById(any())).thenReturn(List.of());

        List<EvidenceItem> results = analysisService.retrieve(INQUIRY_ID, "test query", 5);

        assertThat(results).hasSize(1);
        // Fallback to child content since parent was not found
        assertThat(results.get(0).excerpt()).isEqualTo(childContent);
    }
}
