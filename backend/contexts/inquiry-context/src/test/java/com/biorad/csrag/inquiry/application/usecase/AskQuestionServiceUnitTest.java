package com.biorad.csrag.inquiry.application.usecase;

import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryStatus;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AskQuestionServiceUnitTest {

    @Mock
    private InquiryRepository inquiryRepository;

    @InjectMocks
    private AskQuestionService service;

    @Test
    void ask_createsAndPersistsInquiry_andReturnsResult() {
        AskQuestionCommand command = new AskQuestionCommand("  장비 오류 코드 E-12 문의 ", " email ");

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        when(inquiryRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        AskQuestionResult result = service.ask(command);

        Inquiry saved = captor.getValue();
        assertThat(saved.getQuestion()).isEqualTo("장비 오류 코드 E-12 문의");
        assertThat(saved.getCustomerChannel()).isEqualTo("email");
        assertThat(saved.getStatus()).isEqualTo(InquiryStatus.RECEIVED);

        assertThat(result.inquiryId()).isEqualTo(saved.getId().value().toString());
        assertThat(result.status()).isEqualTo("RECEIVED");
        assertThat(result.message()).contains("Inquiry accepted");

        verify(inquiryRepository).save(saved);
    }

    @Test
    void ask_usesUnspecifiedChannel_whenBlankChannelProvided() {
        AskQuestionCommand command = new AskQuestionCommand("질문", "   ");

        ArgumentCaptor<Inquiry> captor = ArgumentCaptor.forClass(Inquiry.class);
        when(inquiryRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.ask(command);

        assertThat(captor.getValue().getCustomerChannel()).isEqualTo("unspecified");
    }
}
