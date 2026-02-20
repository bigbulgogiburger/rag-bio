package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.interfaces.rest.chunk.ChunkingService;
import com.biorad.csrag.interfaces.rest.document.DocumentTextExtractor.PageText;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrResult;
import com.biorad.csrag.interfaces.rest.document.ocr.OcrService;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import com.biorad.csrag.interfaces.rest.vector.VectorizingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingWorkerTest {

    @Mock private DocumentMetadataJpaRepository documentRepository;
    @Mock private DocumentTextExtractor textExtractor;
    @Mock private OcrService ocrService;
    @Mock private ChunkingService chunkingService;
    @Mock private VectorizingService vectorizingService;
    @Mock private SseService sseService;

    private DocumentIndexingWorker worker;

    @BeforeEach
    void setUp() {
        worker = new DocumentIndexingWorker(
                documentRepository, textExtractor, ocrService, chunkingService, vectorizingService, sseService
        );
    }

    private DocumentMetadataJpaEntity makeDoc(UUID docId, UUID inquiryId, String status, String contentType) {
        return new DocumentMetadataJpaEntity(
                docId, inquiryId, "test.pdf", contentType, 1024L, "/tmp/test.pdf",
                status, null, null, null, null, null,
                Instant.now(), Instant.now()
        );
    }

    @Test
    void indexOneAsync_documentNotFound_skips() {
        UUID docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        worker.indexOneAsync(docId);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void indexOneAsync_statusNotEligible_skips() {
        UUID docId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, UUID.randomUUID(), "INDEXED", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));

        worker.indexOneAsync(docId);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void indexOneAsync_normalPdf_parsesChunksAndVectorizes() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "UPLOADED", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), eq("application/pdf")))
                .thenReturn("This is sufficiently long extracted text from PDF document.");
        when(textExtractor.extractByPage(any(Path.class), eq("application/pdf")))
                .thenReturn(List.of(new PageText(1, "Page 1 content", 0, 14)));
        when(chunkingService.chunkAndStore(eq(docId), anyList(), eq("INQUIRY"), eq(docId)))
                .thenReturn(3);
        when(vectorizingService.upsertDocumentChunks(docId)).thenReturn(3);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        verify(textExtractor).extractByPage(any(Path.class), eq("application/pdf"));
        verify(chunkingService).chunkAndStore(eq(docId), anyList(), eq("INQUIRY"), eq(docId));
        verify(vectorizingService).upsertDocumentChunks(docId);
        // Should save: markParsing, markParsed, markChunked, markIndexed
        verify(documentRepository, atLeast(4)).save(any());
    }

    @Test
    void indexOneAsync_pdfNeedsOcr_usesOcr() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "UPLOADED", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), eq("application/pdf"))).thenReturn("short");
        when(ocrService.extract(any(Path.class))).thenReturn(new OcrResult("OCR extracted text from scanned document", 0.92));
        when(chunkingService.chunkAndStore(eq(docId), anyString())).thenReturn(2);
        when(vectorizingService.upsertDocumentChunks(docId)).thenReturn(2);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        verify(ocrService).extract(any(Path.class));
        verify(vectorizingService).upsertDocumentChunks(docId);
    }

    @Test
    void indexOneAsync_failedParsing_isEligible() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "FAILED_PARSING", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString()))
                .thenReturn("Enough text to not need OCR processing here");
        when(textExtractor.extractByPage(any(Path.class), anyString()))
                .thenReturn(List.of(new PageText(1, "text", 0, 4)));
        when(chunkingService.chunkAndStore(eq(docId), anyList(), eq("INQUIRY"), eq(docId)))
                .thenReturn(1);
        when(vectorizingService.upsertDocumentChunks(docId)).thenReturn(1);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        verify(vectorizingService).upsertDocumentChunks(docId);
    }

    @Test
    void indexOneAsync_exceptionDuringExtraction_marksFailed() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "UPLOADED", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString()))
                .thenThrow(new IOException("Corrupted file"));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        // Should save: markParsing + markFailed
        verify(documentRepository, atLeast(2)).save(any());
    }

    @Test
    void indexOneAsync_sseFailure_doesNotBreakProcessing() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "UPLOADED", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString()))
                .thenReturn("Sufficient text for processing.");
        when(textExtractor.extractByPage(any(Path.class), anyString()))
                .thenReturn(List.of(new PageText(1, "text", 0, 4)));
        when(chunkingService.chunkAndStore(eq(docId), anyList(), eq("INQUIRY"), eq(docId)))
                .thenReturn(1);
        when(vectorizingService.upsertDocumentChunks(docId)).thenReturn(1);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("SSE failure")).when(sseService).send(any(), any(), any());

        worker.indexOneAsync(docId);

        // Processing should complete despite SSE failures
        verify(vectorizingService).upsertDocumentChunks(docId);
    }

    @Test
    void indexOneAsync_longText_isTruncated() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "UPLOADED", "application/pdf");
        String longText = "X".repeat(20_000);
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString())).thenReturn(longText);
        when(textExtractor.extractByPage(any(Path.class), anyString()))
                .thenReturn(List.of(new PageText(1, longText, 0, longText.length())));
        when(chunkingService.chunkAndStore(eq(docId), anyList(), eq("INQUIRY"), eq(docId)))
                .thenReturn(5);
        when(vectorizingService.upsertDocumentChunks(docId)).thenReturn(5);
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        verify(vectorizingService).upsertDocumentChunks(docId);
    }

    @Test
    void indexOneAsync_ocrReturnsNullText_marksFailed() throws Exception {
        UUID docId = UUID.randomUUID();
        UUID inquiryId = UUID.randomUUID();
        DocumentMetadataJpaEntity doc = makeDoc(docId, inquiryId, "UPLOADED", "application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(textExtractor.extract(any(Path.class), anyString())).thenReturn(""); // short => needs OCR
        when(ocrService.extract(any(Path.class))).thenReturn(new OcrResult(null, 0.1));
        when(documentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        worker.indexOneAsync(docId);

        // null text -> limitText throws -> catch block marks failed
        verify(documentRepository, atLeast(2)).save(any());
    }
}
