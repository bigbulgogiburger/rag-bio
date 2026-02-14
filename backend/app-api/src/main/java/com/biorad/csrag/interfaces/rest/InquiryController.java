package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.application.InquiryListService;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionCommand;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionResult;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionUseCase;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    /**
     * 문의 목록 조회 (페이징 + 필터 + 정렬)
     */
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AskQuestionResult createInquiry(@Valid @RequestBody CreateInquiryRequest request) {
        log.info("inquiry.create.request channel={} questionLength={}",
                request.customerChannel(), request.question() == null ? 0 : request.question().length());

        AskQuestionCommand command = new AskQuestionCommand(
                request.question(),
                request.customerChannel() == null ? "unspecified" : request.customerChannel()
        );
        AskQuestionResult result = askQuestionUseCase.ask(command);

        log.info("inquiry.create.success inquiryId={} status={}", result.inquiryId(), result.status());
        return result;
    }

    @GetMapping("/{inquiryId}")
    public InquiryDetailResponse getInquiry(@PathVariable String inquiryId) {
        UUID id = parseInquiryId(inquiryId);
        log.info("inquiry.get.request inquiryId={}", inquiryId);

        Inquiry inquiry = inquiryRepository.findById(new InquiryId(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        log.info("inquiry.get.success inquiryId={} status={}", inquiry.getId().value(), inquiry.getStatus());
        return new InquiryDetailResponse(
                inquiry.getId().value().toString(),
                inquiry.getQuestion(),
                inquiry.getCustomerChannel(),
                inquiry.getStatus().name(),
                inquiry.getCreatedAt()
        );
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inquiryId format");
        }
    }
}
