package com.biorad.csrag.interfaces.rest.sse;

import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.biorad.csrag.common.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Tag(name = "SSE", description = "실시간 이벤트 스트림 API (Server-Sent Events)")
@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/events")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    private final SseService sseService;
    private final InquiryRepository inquiryRepository;

    public SseController(SseService sseService, InquiryRepository inquiryRepository) {
        this.sseService = sseService;
        this.inquiryRepository = inquiryRepository;
    }

    @Operation(summary = "SSE 이벤트 스트림 구독", description = "문의에 대한 실시간 이벤트 스트림을 구독합니다 (인덱싱 진행률, 답변 생성 단계 등)")
    @ApiResponse(responseCode = "200", description = "SSE 스트림 연결 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId
    ) {
        UUID id = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(id))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        log.info("sse.subscribe.request inquiryId={}", inquiryId);
        return sseService.register(id);
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_INQUIRY_ID", "Invalid inquiryId format");
        }
    }
}
