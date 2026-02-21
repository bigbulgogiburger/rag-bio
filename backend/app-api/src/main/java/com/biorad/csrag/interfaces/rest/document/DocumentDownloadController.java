package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.common.exception.ExternalServiceException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentDownloadController {

    private static final Logger log = LoggerFactory.getLogger(DocumentDownloadController.class);

    private final DocumentMetadataJpaRepository documentRepository;
    private final KnowledgeDocumentJpaRepository kbDocRepository;

    public DocumentDownloadController(
            DocumentMetadataJpaRepository documentRepository,
            KnowledgeDocumentJpaRepository kbDocRepository
    ) {
        this.documentRepository = documentRepository;
        this.kbDocRepository = kbDocRepository;
    }

    /**
     * 원본 파일 다운로드
     */
    @GetMapping("/{documentId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID documentId) {
        DocumentInfo info = resolveDocument(documentId);
        Path filePath = Path.of(info.storagePath());

        if (!Files.exists(filePath)) {
            throw new NotFoundException("FILE_NOT_FOUND", "File not found on storage");
        }

        String encodedFileName = URLEncoder.encode(info.fileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFileName)
                .contentType(MediaType.parseMediaType(info.contentType()))
                .body(new FileSystemResource(filePath));
    }

    /**
     * PDF 특정 페이지 추출 다운로드.
     * 비-PDF 파일은 전체 파일을 반환한다.
     */
    @GetMapping("/{documentId}/pages")
    public ResponseEntity<Resource> downloadPages(
            @PathVariable UUID documentId,
            @RequestParam int from,
            @RequestParam int to,
            @RequestParam(defaultValue = "false") boolean download
    ) {
        if (from < 1 || to < from) {
            throw new ValidationException("INVALID_PAGE_RANGE",
                    "Invalid page range: from=" + from + " to=" + to);
        }

        DocumentInfo info = resolveDocument(documentId);
        Path filePath = Path.of(info.storagePath());

        if (!Files.exists(filePath)) {
            throw new NotFoundException("FILE_NOT_FOUND", "File not found on storage");
        }

        // 비-PDF: 전체 파일 반환
        if (!info.contentType().toLowerCase().contains("pdf")) {
            return download(documentId);
        }

        try {
            byte[] pdfBytes = extractPdfPages(filePath, from, to);

            String baseName = info.fileName().replaceFirst("\\.[^.]+$", "");
            String pageFileName = baseName + "_p" + from + "-" + to + ".pdf";
            String encodedFileName = URLEncoder.encode(pageFileName, StandardCharsets.UTF_8)
                    .replace("+", "%20");

            log.info("document.pages.download documentId={} from={} to={}", documentId, from, to);

            String disposition = download ? "attachment" : "inline";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename*=UTF-8''" + encodedFileName)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(new ByteArrayResource(pdfBytes));
        } catch (IOException e) {
            throw new ExternalServiceException("PDFExtractor",
                    "Failed to extract PDF pages");
        }
    }

    private byte[] extractPdfPages(Path filePath, int from, int to) throws IOException {
        try (PDDocument source = Loader.loadPDF(filePath.toFile())) {
            int totalPages = source.getNumberOfPages();
            int actualTo = Math.min(to, totalPages);

            if (from > totalPages) {
                throw new ValidationException("INVALID_PAGE_RANGE",
                        "Page " + from + " exceeds total pages " + totalPages);
            }

            try (PDDocument extracted = new PDDocument()) {
                for (int i = from; i <= actualTo; i++) {
                    PDPage page = source.getPage(i - 1); // 0-based
                    extracted.addPage(page);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                extracted.save(baos);
                return baos.toByteArray();
            }
        }
    }

    /**
     * documents 또는 knowledge_documents 테이블에서 문서 정보를 조회한다.
     */
    private DocumentInfo resolveDocument(UUID documentId) {
        // Inquiry 문서 먼저 조회
        return documentRepository.findById(documentId)
                .map(d -> new DocumentInfo(d.getStoragePath(), d.getFileName(), d.getContentType()))
                .or(() -> kbDocRepository.findById(documentId)
                        .map(d -> new DocumentInfo(d.getStoragePath(), d.getFileName(), d.getContentType())))
                .orElseThrow(() -> new NotFoundException("DOCUMENT_NOT_FOUND",
                        "Document not found: " + documentId));
    }

    private record DocumentInfo(String storagePath, String fileName, String contentType) {}
}
