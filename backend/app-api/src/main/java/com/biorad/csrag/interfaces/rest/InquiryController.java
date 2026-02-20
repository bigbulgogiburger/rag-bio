package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.application.InquiryListService;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionCommand;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionResult;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionUseCase;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Inquiry", description = "문의 관리 API")
@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {

    private static final Logger log = LoggerFactory.getLogger(InquiryController.class);

    private final AskQuestionUseCase askQuestionUseCase;
    private final InquiryRepository inquiryRepository;
    private final InquiryListService inquiryListService;

    public InquiryController(
            AskQuestionUseCase askQuestionUseCase,
            InquiryRepository inquiryRepository,
            InquiryListService inquiryListService
    ) {
        this.askQuestionUseCase = askQuestionUseCase;
        this.inquiryRepository = inquiryRepository;
        this.inquiryListService = inquiryListService;
    }

    @Operation(summary = "문의 목록 조회", description = "페이징, 필터, 정렬을 지원하는 문의 목록 조회")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @GetMapping
    public ResponseEntity<InquiryListResponse> listInquiries(
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable
    ) {
        log.info("inquiry.list.request page={} size={} filters=[status={}, channel={}, keyword={}, from={}, to={}]",
                pageable.getPageNumber(), pageable.getPageSize(), status, channel, keyword, from, to);

        InquiryListResponse response = inquiryListService.list(
            status, channel, keyword, from, to, pageable
        );

        log.info("inquiry.list.success totalElements={} totalPages={}", response.totalElements(), response.totalPages());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "문의 생성", description = "새 문의를 생성합니다")
    @ApiResponse(responseCode = "201", description = "생성 성공")
    @ApiResponse(responseCode = "400", description = "유효성 검증 실패")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AskQuestionResult createInquiry(@Valid @RequestBody CreateInquiryRequest request) {
        log.info("inquiry.create.request channel={} questionLength={}",
                request.customerChannel(), request.question() == null ? 0 : request.question().length());

        AskQuestionCommand command = new AskQuestionCommand(
                request.question(),
                request.customerChannel() == null ? "unspecified" : request.customerChannel(),
                request.preferredTone()
        );
        AskQuestionResult result = askQuestionUseCase.ask(command);

        log.info("inquiry.create.success inquiryId={} status={}", result.inquiryId(), result.status());
        return result;
    }

    @Operation(summary = "문의 상세 조회", description = "ID로 문의 상세 정보를 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping("/{inquiryId}")
    public InquiryDetailResponse getInquiry(@Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId) {
        UUID id = parseInquiryId(inquiryId);
        log.info("inquiry.get.request inquiryId={}", inquiryId);

        Inquiry inquiry = inquiryRepository.findById(new InquiryId(id))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        log.info("inquiry.get.success inquiryId={} status={}", inquiry.getId().value(), inquiry.getStatus());
        return new InquiryDetailResponse(
                inquiry.getId().value().toString(),
                inquiry.getQuestion(),
                inquiry.getCustomerChannel(),
                inquiry.getPreferredTone(),
                inquiry.getStatus().name(),
                inquiry.getCreatedAt()
        );
    }

    @Operation(summary = "문의 수정", description = "문의 질문 내용을 수정합니다")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @PatchMapping("/{inquiryId}")
    public InquiryDetailResponse updateInquiry(
            @PathVariable String inquiryId,
            @Valid @RequestBody UpdateInquiryRequest request
    ) {
        UUID id = parseInquiryId(inquiryId);
        log.info("inquiry.update.request inquiryId={}", inquiryId);

        Inquiry inquiry = inquiryRepository.findById(new InquiryId(id))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        inquiry.updateQuestion(request.question());
        Inquiry saved = inquiryRepository.save(inquiry);

        log.info("inquiry.update.success inquiryId={}", inquiryId);
        return new InquiryDetailResponse(
                saved.getId().value().toString(),
                saved.getQuestion(),
                saved.getCustomerChannel(),
                saved.getPreferredTone(),
                saved.getStatus().name(),
                saved.getCreatedAt()
        );
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_INQUIRY_ID", "Invalid inquiryId format");
        }
    }
}
