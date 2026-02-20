package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.application.knowledge.KnowledgeBaseService;
import com.biorad.csrag.interfaces.rest.dto.knowledge.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Tag(name = "Knowledge Base", description = "지식 기반 문서 관리 API")
@RestController
@RequestMapping("/api/v1/knowledge-base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Operation(summary = "KB 문서 업로드", description = "지식 기반 문서를 업로드합니다 (메타데이터 포함)")
    @ApiResponse(responseCode = "201", description = "업로드 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검증 실패")
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

    @Operation(summary = "KB 문서 목록 조회", description = "지식 기반 문서 목록을 페이징/필터로 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
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

    @Operation(summary = "KB 문서 상세 조회", description = "지식 기반 문서의 상세 정보를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    @GetMapping("/documents/{docId}")
    public ResponseEntity<KbDocumentResponse> detail(
            @Parameter(description = "문서 ID (UUID)") @PathVariable UUID docId
    ) {
        KbDocumentResponse response = knowledgeBaseService.getDetail(docId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "KB 문서 삭제", description = "지식 기반 문서와 관련 청크/벡터를 삭제합니다")
    @ApiResponse(responseCode = "204", description = "삭제 성공")
    @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<Void> delete(
            @Parameter(description = "문서 ID (UUID)") @PathVariable UUID docId
    ) {
        knowledgeBaseService.delete(docId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "AI 메타데이터 분석", description = "AI를 사용하여 기존 문서의 메타데이터를 분석합니다")
    @ApiResponse(responseCode = "200", description = "분석 성공")
    @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    @PostMapping("/documents/{docId}/analyze-metadata")
    public ResponseEntity<KbDocumentResponse> analyzeMetadata(
            @Parameter(description = "문서 ID (UUID)") @PathVariable UUID docId
    ) {
        KbDocumentResponse response = knowledgeBaseService.analyzeMetadata(docId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "개별 문서 인덱싱", description = "지식 기반 문서 하나를 비동기로 인덱싱합니다")
    @ApiResponse(responseCode = "202", description = "인덱싱 시작됨")
    @ApiResponse(responseCode = "404", description = "문서를 찾을 수 없음")
    @PostMapping("/documents/{docId}/indexing/run")
    public ResponseEntity<KbIndexingResponse> indexOne(
            @Parameter(description = "문서 ID (UUID)") @PathVariable UUID docId
    ) {
        KbIndexingResponse response = knowledgeBaseService.indexOne(docId);
        return ResponseEntity.accepted().body(response);
    }

    @Operation(summary = "일괄 인덱싱", description = "미인덱싱 문서를 일괄 비동기 인덱싱합니다")
    @ApiResponse(responseCode = "202", description = "인덱싱 시작됨")
    @PostMapping("/indexing/run")
    public ResponseEntity<KbBatchIndexingResponse> indexAll() {
        KbBatchIndexingResponse response = knowledgeBaseService.indexAll();
        return ResponseEntity.accepted().body(response);
    }

    @Operation(summary = "KB 통계 조회", description = "지식 기반 통계 (카테고리별, 제품군별, 전체)를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping("/stats")
    public ResponseEntity<KbStatsResponse> stats() {
        KbStatsResponse response = knowledgeBaseService.getStats();
        return ResponseEntity.ok(response);
    }
}
