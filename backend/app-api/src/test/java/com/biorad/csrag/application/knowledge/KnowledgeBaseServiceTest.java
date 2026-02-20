package com.biorad.csrag.application.knowledge;

import com.biorad.csrag.common.exception.ConflictException;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.dto.knowledge.*;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeBaseServiceTest {

    @Mock private KnowledgeDocumentJpaRepository kbDocRepository;
    @Mock private DocumentChunkJpaRepository chunkRepository;
    @Mock private VectorStore vectorStore;
    @Mock private DocumentMetadataAnalyzer metadataAnalyzer;
    @Mock private KnowledgeIndexingWorker indexingWorker;

    private KnowledgeBaseService service;

    @BeforeEach
    void setUp() {
        service = new KnowledgeBaseService(
                kbDocRepository, chunkRepository, vectorStore, metadataAnalyzer, indexingWorker
        );
    }

    @Test
    void upload_emptyFile_throwsValidationException() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.upload(emptyFile, "Title", "MANUAL", null, null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("File is empty");
    }

    @Test
    void getDetail_notFound_throws() {
        UUID docId = UUID.randomUUID();
        when(kbDocRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(docId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getDetail_success() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity entity = KnowledgeDocumentJpaEntity.create(
                "Test Manual", "MANUAL", "qPCR", "test.pdf", "application/pdf",
                1024L, "/path/test.pdf", "Description", "tags", "user-1"
        );
        when(kbDocRepository.findById(any())).thenReturn(Optional.of(entity));

        KbDocumentResponse result = service.getDetail(docId);

        assertThat(result.title()).isEqualTo("Test Manual");
        assertThat(result.category()).isEqualTo("MANUAL");
    }

    @Test
    void delete_removesChunksVectorsFileAndEntity() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity entity = KnowledgeDocumentJpaEntity.create(
                "Title", "FAQ", null, "faq.pdf", "application/pdf",
                512L, "/tmp/faq.pdf", null, null, null
        );
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(entity));

        service.delete(docId);

        verify(chunkRepository).deleteByDocumentId(docId);
        verify(vectorStore).deleteByDocumentId(docId);
        verify(kbDocRepository).delete(entity);
    }

    @Test
    void delete_notFound_throws() {
        UUID docId = UUID.randomUUID();
        when(kbDocRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(docId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_vectorStoreFailure_doesNotPreventDeletion() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity entity = KnowledgeDocumentJpaEntity.create(
                "Title", "FAQ", null, "faq.pdf", "application/pdf",
                512L, "/tmp/nonexistent.pdf", null, null, null
        );
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(entity));
        doThrow(new RuntimeException("Vector store down")).when(vectorStore).deleteByDocumentId(docId);

        service.delete(docId);

        verify(kbDocRepository).delete(entity);
    }

    @Test
    void getStats_returnsAggregates() {
        when(kbDocRepository.count()).thenReturn(10L);
        when(kbDocRepository.countByStatus("INDEXED")).thenReturn(7);
        when(chunkRepository.countBySourceType("KNOWLEDGE_BASE")).thenReturn(100L);
        when(kbDocRepository.findAll()).thenReturn(List.of());

        var stats = service.getStats();

        assertThat(stats.totalDocuments()).isEqualTo(10L);
        assertThat(stats.indexedDocuments()).isEqualTo(7L);
        assertThat(stats.totalChunks()).isEqualTo(100L);
    }

    @Test
    void getStats_withDocuments_groupsByCategoryAndProductFamily() {
        KnowledgeDocumentJpaEntity doc1 = KnowledgeDocumentJpaEntity.create(
                "Manual 1", "MANUAL", "qPCR", "m1.pdf", "application/pdf", 1024L, "/p/m1.pdf", null, null, null
        );
        KnowledgeDocumentJpaEntity doc2 = KnowledgeDocumentJpaEntity.create(
                "Manual 2", "MANUAL", "ddPCR", "m2.pdf", "application/pdf", 2048L, "/p/m2.pdf", null, null, null
        );
        KnowledgeDocumentJpaEntity doc3 = KnowledgeDocumentJpaEntity.create(
                "FAQ 1", "FAQ", "qPCR", "f1.pdf", "application/pdf", 512L, "/p/f1.pdf", null, null, null
        );
        when(kbDocRepository.count()).thenReturn(3L);
        when(kbDocRepository.countByStatus("INDEXED")).thenReturn(2);
        when(chunkRepository.countBySourceType("KNOWLEDGE_BASE")).thenReturn(30L);
        when(kbDocRepository.findAll()).thenReturn(List.of(doc1, doc2, doc3));

        KbStatsResponse stats = service.getStats();

        assertThat(stats.byCategory()).containsEntry("MANUAL", 2L);
        assertThat(stats.byCategory()).containsEntry("FAQ", 1L);
        assertThat(stats.byProductFamily()).containsEntry("qPCR", 2L);
        assertThat(stats.byProductFamily()).containsEntry("ddPCR", 1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void list_returnsPaginatedResults() {
        KnowledgeDocumentJpaEntity doc = KnowledgeDocumentJpaEntity.create(
                "Test Manual", "MANUAL", "qPCR", "test.pdf", "application/pdf",
                1024L, "/path/test.pdf", "Description", "tags", "user-1"
        );
        Pageable pageable = PageRequest.of(0, 10);
        when(kbDocRepository.findAll(any(Specification.class), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(doc), pageable, 1));

        KbDocumentListResponse result = service.list(null, null, null, null, pageable);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).title()).isEqualTo("Test Manual");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    void indexOne_notFound_throws() {
        UUID docId = UUID.randomUUID();
        when(kbDocRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.indexOne(docId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void indexOne_alreadyIndexing_throwsConflict() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity doc = KnowledgeDocumentJpaEntity.create(
                "Title", "MANUAL", null, "test.pdf", "application/pdf",
                1024L, "/p/test.pdf", null, null, null
        );
        doc.markIndexing();
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.indexOne(docId))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void analyzeMetadata_notFound_throws() {
        UUID docId = UUID.randomUUID();
        when(kbDocRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyzeMetadata(docId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void analyzeMetadata_success_delegatesToAnalyzer() {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity entity = KnowledgeDocumentJpaEntity.create(
                "Test", "MANUAL", null, "test.pdf", "application/pdf",
                1024L, "/tmp/test.pdf", null, null, null
        );
        when(kbDocRepository.findById(any())).thenReturn(Optional.of(entity));
        when(metadataAnalyzer.isAvailable()).thenReturn(false);

        KbDocumentResponse result = service.analyzeMetadata(docId);

        assertThat(result.title()).isEqualTo("Test");
    }
}
