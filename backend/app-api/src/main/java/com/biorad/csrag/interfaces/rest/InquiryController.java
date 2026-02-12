package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.inquiry.application.usecase.AskQuestionCommand;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionResult;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionUseCase;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {

    private final AskQuestionUseCase askQuestionUseCase;
    private final InquiryRepository inquiryRepository;

    public InquiryController(AskQuestionUseCase askQuestionUseCase, InquiryRepository inquiryRepository) {
        this.askQuestionUseCase = askQuestionUseCase;
        this.inquiryRepository = inquiryRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AskQuestionResult createInquiry(@Valid @RequestBody CreateInquiryRequest request) {
        AskQuestionCommand command = new AskQuestionCommand(
                request.question(),
                request.customerChannel() == null ? "unspecified" : request.customerChannel()
        );
        return askQuestionUseCase.ask(command);
    }

    @GetMapping("/{inquiryId}")
    public InquiryDetailResponse getInquiry(@PathVariable String inquiryId) {
        UUID id = parseInquiryId(inquiryId);
        Inquiry inquiry = inquiryRepository.findById(new InquiryId(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

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
