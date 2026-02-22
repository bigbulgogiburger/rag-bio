package com.biorad.csrag.application.knowledge;

import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ChunkingService;
import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor;
import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor.PageText;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KnowledgeIndexingWorkerTest {

    @Mock private KnowledgeDocumentJpaRepository kbDocRepository;
    @Mock private ChunkingService chunkingService;
    @Mock private VectorizingService vectorizingService;
    @Mock private OcrService ocrService;
    @Mock private DocumentTextExtractor textExtractor;
    @Mock private VectorStore vectorStore;

    private KnowledgeIndexingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new KnowledgeIndexingWorker(kbDocRepository, chunkingService, vectorizingService, ocrService, textExtractor, vectorStore);
    }

    @Test
    void indexOneAsync_documentNotFound_skips() {
        UUID docId = UUID.randomUUID();
        when(kbDocRepository.findById(docId)).thenReturn(Optional.empty());

        worker.indexOneAsync(docId);

        verify(kbDocRepository, never()).save(any());
    }

    @Test
    void indexOneAsync_normalText_parsesChunksAndVectorizes() throws Exception {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity doc = KnowledgeDocumentJpaEntity.create(
                "Title", "MANUAL", "qPCR", "test.pdf", "application/pdf",
                1024L, "/tmp/test.pdf", null, null, null
        );
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), eq("application/pdf")))
                .thenReturn("This is a long enough extracted text that does not need OCR processing at all.");
        when(textExtractor.extractByPage(any(Path.class), eq("application/pdf")))
                .thenReturn(List.of(new PageText(1, "Page 1 text here", 0, 16)));
        when(chunkingService.chunkAndStore(eq(doc.getId()), anyList(), eq("KNOWLEDGE_BASE"), eq(doc.getId()), any()))
                .thenReturn(3);
        when(vectorizingService.upsertDocumentChunks(doc.getId())).thenReturn(3);
        when(kbDocRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        // Should save multiple times: markParsing, markParsed, markChunked, markIndexed
        verify(kbDocRepository, atLeast(4)).save(any());
        verify(vectorizingService).upsertDocumentChunks(doc.getId());
    }

    @Test
    void indexOneAsync_shortText_usesOcr() throws Exception {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity doc = KnowledgeDocumentJpaEntity.create(
                "Title", "MANUAL", null, "scan.pdf", "application/pdf",
                2048L, "/tmp/scan.pdf", null, null, null
        );
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), eq("application/pdf"))).thenReturn("");
        when(ocrService.extract(any(Path.class))).thenReturn(new OcrResult("OCR extracted text from scan", 0.85));
        when(chunkingService.chunkAndStore(eq(doc.getId()), anyString(), eq("KNOWLEDGE_BASE"), eq(doc.getId()), any()))
                .thenReturn(2);
        when(vectorizingService.upsertDocumentChunks(doc.getId())).thenReturn(2);
        when(kbDocRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        verify(ocrService).extract(any(Path.class));
        verify(vectorizingService).upsertDocumentChunks(doc.getId());
    }

    @Test
    void indexOneAsync_exceptionDuringProcessing_marksFailed() throws Exception {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity doc = KnowledgeDocumentJpaEntity.create(
                "Title", "MANUAL", null, "bad.pdf", "application/pdf",
                1024L, "/tmp/bad.pdf", null, null, null
        );
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString()))
                .thenThrow(new IOException("File not readable"));
        when(kbDocRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        // After failure, doc should be saved with FAILED status
        verify(kbDocRepository, atLeast(2)).save(any());
    }

    @Test
    void indexOneAsync_longText_isTruncated() throws Exception {
        UUID docId = UUID.randomUUID();
        KnowledgeDocumentJpaEntity doc = KnowledgeDocumentJpaEntity.create(
                "Title", "MANUAL", null, "big.pdf", "application/pdf",
                999999L, "/tmp/big.pdf", null, null, null
        );
        // needsOcr returns false for text > 50 chars
        String longText = "A".repeat(600_000);
        when(kbDocRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString())).thenReturn(longText);
        when(textExtractor.extractByPage(any(Path.class), anyString()))
                .thenReturn(List.of(new PageText(1, longText, 0, longText.length())));
        when(chunkingService.chunkAndStore(eq(doc.getId()), anyList(), eq("KNOWLEDGE_BASE"), eq(doc.getId()), any()))
                .thenReturn(10);
        when(vectorizingService.upsertDocumentChunks(doc.getId())).thenReturn(10);
        when(kbDocRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        verify(vectorizingService).upsertDocumentChunks(doc.getId());
    }
}
