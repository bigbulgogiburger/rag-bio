package com.biorad.csrag.interfaces.rest;

import com.biorad.csrag.inquiry.application.usecase.AskQuestionCommand;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionResult;
import com.biorad.csrag.inquiry.application.usecase.AskQuestionUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inquiries")
public class InquiryController {

    private final AskQuestionUseCase askQuestionUseCase;

    public InquiryController(AskQuestionUseCase askQuestionUseCase) {
        this.askQuestionUseCase = askQuestionUseCase;
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
}
