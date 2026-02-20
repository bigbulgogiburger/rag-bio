package com.biorad.csrag.interfaces.rest.analysis;

import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Analysis", description = "문의 분석 API (Retrieve + Verify)")
@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/analysis")
public class AnalysisController {

    private final InquiryRepository inquiryRepository;
    private final AnalysisService analysisService;

    public AnalysisController(InquiryRepository inquiryRepository, AnalysisService analysisService) {
        this.inquiryRepository = inquiryRepository;
        this.analysisService = analysisService;
    }

    @Operation(summary = "문의 분석 실행", description = "벡터 검색 + 검증을 통해 관련 근거를 분석합니다 (Deprecated: 답변 초안 생성 시 자동 실행)")
    @ApiResponse(responseCode = "200", description = "분석 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @Deprecated
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AnalyzeResponse analyze(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Valid @RequestBody AnalyzeRequest request
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        int topK = request.topK() == null ? 5 : request.topK();
        return analysisService.analyze(inquiryUuid, request.question(), topK);
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_INQUIRY_ID", "Invalid inquiryId format");
        }
    }
}
