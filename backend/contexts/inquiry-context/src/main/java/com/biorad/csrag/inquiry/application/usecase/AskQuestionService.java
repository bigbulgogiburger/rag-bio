package com.biorad.csrag.inquiry.application.usecase;

import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AskQuestionService implements AskQuestionUseCase {

    private final InquiryRepository inquiryRepository;

    public AskQuestionService(InquiryRepository inquiryRepository) {
        this.inquiryRepository = inquiryRepository;
    }

    @Override
    @Transactional
    public AskQuestionResult ask(AskQuestionCommand command) {
        Inquiry inquiry = Inquiry.create(command.question(), command.customerChannel(), command.preferredTone());
        Inquiry saved = inquiryRepository.save(inquiry);
        return new AskQuestionResult(
                saved.getId().value().toString(),
                saved.getStatus().name(),
                "Inquiry accepted and queued for retrieval/verifier/composer orchestration"
        );
    }
}
