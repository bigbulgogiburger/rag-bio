package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.application.knowledge.KnowledgeBaseService;
import com.biorad.csrag.interfaces.rest.dto.knowledge.*;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Knowledge Base 관리 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 문서 업로드
     */
    @PostMapping("/documents")
    public ResponseEntity<KbDocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart("title") String title,
            @RequestPart("category") String category,
            @RequestPart(value = "productFamily", required = false) String productFamily,
            @RequestPart(value = "description", required = false) String description,
            @RequestPart(value = "tags", required = false) String tags,
            @RequestPart(value = "uploadedBy", required = false) String uploadedBy
    ) {
        KbDocumentResponse response = knowledgeBaseService.upload(
                file, title, category, productFamily, description, tags, uploadedBy
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 문서 목록 조회
     */
    @GetMapping("/documents")
    public ResponseEntity<KbDocumentListResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String productFamily,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        KbDocumentListResponse response = knowledgeBaseService.list(
                category, productFamily, status, keyword, pageable
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 문서 상세 조회
     */
    @GetMapping("/documents/{docId}")
    public ResponseEntity<KbDocumentResponse> detail(@PathVariable UUID docId) {
        KbDocumentResponse response = knowledgeBaseService.getDetail(docId);
        return ResponseEntity.ok(response);
    }

    /**
     * 문서 삭제
     */
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> delete(@PathVariable UUID docId) {
        knowledgeBaseService.delete(docId);
        return ResponseEntity.noContent().build();
    }

    /**
     * AI 메타데이터 분석 (기존 문서 대상)
     */
    @PostMapping("/documents/{docId}/analyze-metadata")
    public ResponseEntity<KbDocumentResponse> analyzeMetadata(@PathVariable UUID docId) {
        KbDocumentResponse response = knowledgeBaseService.analyzeMetadata(docId);
        return ResponseEntity.ok(response);
    }

    /**
     * 개별 문서 인덱싱 (비동기)
     */
    @PostMapping("/documents/{docId}/indexing/run")
    public ResponseEntity<KbIndexingResponse> indexOne(@PathVariable UUID docId) {
        KbIndexingResponse response = knowledgeBaseService.indexOne(docId);
        return ResponseEntity.accepted().body(response);
    }

    /**
     * 미인덱싱 문서 일괄 인덱싱 (비동기)
     */
    @PostMapping("/indexing/run")
    public ResponseEntity<KbBatchIndexingResponse> indexAll() {
        KbBatchIndexingResponse response = knowledgeBaseService.indexAll();
        return ResponseEntity.accepted().body(response);
    }

    /**
     * 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<KbStatsResponse> stats() {
        KbStatsResponse response = knowledgeBaseService.getStats();
        return ResponseEntity.ok(response);
    }
}
