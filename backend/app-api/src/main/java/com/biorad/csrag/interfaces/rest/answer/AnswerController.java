package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/answers")
public class AnswerController {

    private final InquiryRepository inquiryRepository;
    private final AnswerComposerService answerComposerService;

    public AnswerController(InquiryRepository inquiryRepository, AnswerComposerService answerComposerService) {
        this.inquiryRepository = inquiryRepository;
        this.answerComposerService = answerComposerService;
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse draft(
            @PathVariable String inquiryId,
            @Valid @RequestBody AnswerDraftRequest request
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        return answerComposerService.compose(inquiryUuid, request.question(), request.tone(), request.channel());
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inquiryId format");
        }
    }
}
