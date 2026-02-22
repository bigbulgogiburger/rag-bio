package com.biorad.csrag.interfaces.rest.document;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.common.exception.ExternalServiceException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Tag(name = "Document", description = "문의 첨부 문서 관리 API")
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
    private final DocumentIndexingWorker documentIndexingWorker;

    @Value("${app.storage.upload-dir:uploads}")
    private String uploadDir;

    public DocumentController(
            InquiryRepository inquiryRepository,
            DocumentMetadataJpaRepository documentMetadataJpaRepository,
            DocumentIndexingService documentIndexingService,
            DocumentIndexingWorker documentIndexingWorker
    ) {
        this.inquiryRepository = inquiryRepository;
        this.documentMetadataJpaRepository = documentMetadataJpaRepository;
        this.documentIndexingService = documentIndexingService;
        this.documentIndexingWorker = documentIndexingWorker;
    }

    @Operation(summary = "문서 업로드", description = "문의에 PDF/DOC/DOCX 문서를 업로드합니다 (자동 인덱싱 트리거)")
    @ApiResponse(responseCode = "201", description = "업로드 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검증 실패 (빈 파일 또는 허용되지 않는 파일 형식)")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentUploadResponse upload(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @RequestParam("file") MultipartFile file
    ) {
        log.info("document.upload.request inquiryId={} fileName={} size={} contentType={}",
                inquiryId,
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType());

        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        if (file.isEmpty()) {
            throw new ValidationException("FILE_EMPTY", "File must not be empty");
        }

        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ValidationException("INVALID_FILE_TYPE", "Only PDF/DOC/DOCX files are allowed");
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

            // Trigger async indexing automatically after upload
            documentIndexingWorker.indexOneAsync(documentId);
            log.info("document.upload.auto-indexing.triggered inquiryId={} documentId={}", inquiryUuid, documentId);

            return new DocumentUploadResponse(documentId.toString(), inquiryUuid.toString(), fileName, "UPLOADED");
        } catch (IOException e) {
            throw new ExternalServiceException("FileStorage", "Failed to store uploaded file");
        }
    }

    @Operation(summary = "문서 목록 조회", description = "문의에 첨부된 문서 목록을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping
    public List<DocumentStatusResponse> list(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId
    ) {
        return getDocumentStatuses(inquiryId);
    }

    @Operation(summary = "인덱싱 상태 조회", description = "문의 첨부 문서의 인덱싱 진행 상태를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping("/indexing-status")
    public InquiryIndexingStatusResponse indexingStatus(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId
    ) {
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

    @Operation(summary = "인덱싱 실행", description = "문의 첨부 문서의 인덱싱을 수동으로 트리거합니다")
    @ApiResponse(responseCode = "200", description = "인덱싱 실행 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "모든 문서가 이미 인덱싱 중 또는 완료")
    @PostMapping("/indexing/run")
    public ResponseEntity<?> runIndexing(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @RequestParam(name = "failedOnly", defaultValue = "false") boolean failedOnly,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        log.info("document.indexing.run.request inquiryId={} failedOnly={} force={}", inquiryId, failedOnly, force);
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        // force 모드가 아닐 때만 conflict 체크
        if (!force) {
            Set<String> indexingStates = Set.of("PARSING", "PARSED", "PARSED_OCR", "CHUNKED");
            List<DocumentMetadataJpaEntity> docs = documentMetadataJpaRepository.findByInquiryIdOrderByCreatedAtDesc(inquiryUuid);
            boolean allInProgress = !docs.isEmpty() && docs.stream()
                    .allMatch(d -> indexingStates.contains(d.getStatus()) || "INDEXED".equals(d.getStatus()));
            if (allInProgress) {
                log.info("document.indexing.run.conflict inquiryId={} reason=all_documents_already_indexing_or_indexed", inquiryUuid);
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "All documents are already being indexed or have been indexed."));
            }
        }

        IndexingRunResponse response = documentIndexingService.run(inquiryUuid, failedOnly, force);
        log.info("document.indexing.run.success inquiryId={} processed={} succeeded={} failed={}",
                response.inquiryId(), response.processed(), response.succeeded(), response.failed());
        return ResponseEntity.ok(response);
    }

    private List<DocumentStatusResponse> getDocumentStatuses(String inquiryId) {
        log.info("document.list.request inquiryId={}", inquiryId);
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

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
            throw new ValidationException("INVALID_INQUIRY_ID", "Invalid inquiryId format");
        }
    }
}
