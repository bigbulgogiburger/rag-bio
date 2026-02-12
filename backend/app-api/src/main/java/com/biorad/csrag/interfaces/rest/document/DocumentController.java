package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final InquiryRepository inquiryRepository;
    private final DocumentMetadataJpaRepository documentMetadataJpaRepository;
    private final DocumentIndexingService documentIndexingService;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public DocumentController(
            InquiryRepository inquiryRepository,
            DocumentMetadataJpaRepository documentMetadataJpaRepository,
            DocumentIndexingService documentIndexingService
    ) {
        this.inquiryRepository = inquiryRepository;
        this.documentMetadataJpaRepository = documentMetadataJpaRepository;
        this.documentIndexingService = documentIndexingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentUploadResponse upload(
            @PathVariable String inquiryId,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("document.upload.request inquiryId={} fileName={} size={} contentType={}",
                inquiryId,
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType());

        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PDF/DOC/DOCX files are allowed");
        }

        String fileName = StringUtils.cleanPath(file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename());
        UUID documentId = UUID.randomUUID();

        try {
            Path baseDir = Path.of(uploadDir, inquiryUuid.toString());
            Files.createDirectories(baseDir);

            Path target = baseDir.resolve(documentId + "_" + fileName);
            file.transferTo(target);

            Instant now = Instant.now();
            DocumentMetadataJpaEntity entity = new DocumentMetadataJpaEntity(
                    documentId,
                    inquiryUuid,
                    fileName,
                    contentType,
                    file.getSize(),
                    target.toString(),
                    "UPLOADED",
                    null,
                    null,
                    null,
                    null,
                    null,
                    now,
                    now
            );
            documentMetadataJpaRepository.save(entity);
            log.info("document.upload.success inquiryId={} documentId={} status=UPLOADED", inquiryUuid, documentId);

            return new DocumentUploadResponse(documentId.toString(), inquiryUuid.toString(), fileName, "UPLOADED");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store uploaded file", e);
        }
    }

    @GetMapping
    public List<DocumentStatusResponse> list(@PathVariable String inquiryId) {
        return getDocumentStatuses(inquiryId);
    }

    @GetMapping("/indexing-status")
    public InquiryIndexingStatusResponse indexingStatus(@PathVariable String inquiryId) {
        log.info("document.indexing.status.request inquiryId={}", inquiryId);
        List<DocumentStatusResponse> docs = getDocumentStatuses(inquiryId);

        int uploaded = (int) docs.stream().filter(d -> "UPLOADED".equals(d.status())).count();
        int parsing = (int) docs.stream().filter(d -> "PARSING".equals(d.status())).count();
        int parsed = (int) docs.stream().filter(d -> "PARSED".equals(d.status()) || "PARSED_OCR".equals(d.status())).count();
        int chunked = (int) docs.stream().filter(d -> "CHUNKED".equals(d.status())).count();
        int indexed = (int) docs.stream().filter(d -> "INDEXED".equals(d.status())).count();
        int failed = (int) docs.stream().filter(d -> "FAILED_PARSING".equals(d.status())).count();

        return new InquiryIndexingStatusResponse(
                inquiryId,
                docs.size(),
                uploaded,
                parsing,
                parsed,
                chunked,
                indexed,
                failed,
                docs
        );
    }

    @PostMapping("/indexing/run")
    public IndexingRunResponse runIndexing(
            @PathVariable String inquiryId,
            @RequestParam(name = "failedOnly", defaultValue = "false") boolean failedOnly
    ) {
        log.info("document.indexing.run.request inquiryId={} failedOnly={}", inquiryId, failedOnly);
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        IndexingRunResponse response = documentIndexingService.run(inquiryUuid, failedOnly);
        log.info("document.indexing.run.success inquiryId={} processed={} succeeded={} failed={}",
                response.inquiryId(), response.processed(), response.succeeded(), response.failed());
        return response;
    }

    private List<DocumentStatusResponse> getDocumentStatuses(String inquiryId) {
        log.info("document.list.request inquiryId={}", inquiryId);
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        return documentMetadataJpaRepository.findByInquiryIdOrderByCreatedAtDesc(inquiryUuid)
                .stream()
                .map(document -> new DocumentStatusResponse(
                        document.getId().toString(),
                        document.getInquiryId().toString(),
                        document.getFileName(),
                        document.getContentType(),
                        document.getFileSize(),
                        document.getStatus(),
                        document.getCreatedAt(),
                        document.getUpdatedAt(),
                        document.getLastError(),
                        document.getOcrConfidence(),
                        document.getChunkCount(),
                        document.getVectorCount()
                ))
                .toList();
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inquiryId format");
        }
    }
}
