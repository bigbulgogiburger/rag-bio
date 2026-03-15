package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.interfaces.rest.answer.orchestration.PipelineStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "Pipeline Status", description = "파이프라인 실행 상태 조회 API")
@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}")
public class PipelineStatusController {

    private final PipelineStatusService pipelineStatusService;

    public PipelineStatusController(PipelineStatusService pipelineStatusService) {
        this.pipelineStatusService = pipelineStatusService;
    }

    @Operation(summary = "파이프라인 상태 조회", description = "답변 생성 파이프라인의 현재 실행 상태를 조회합니다")
    @ApiResponse(responseCode = "200", description = "상태 조회 성공")
    @GetMapping("/pipeline-status")
    @ResponseStatus(HttpStatus.OK)
    public PipelineStatusResponse getPipelineStatus(
            @Parameter(description = "문의 ID (UUID)") @PathVariable UUID inquiryId
    ) {
        return pipelineStatusService.getStatus(inquiryId);
    }
}
