package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaEntity;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaRepository;
import com.biorad.csrag.interfaces.rest.answer.orchestration.AnswerOrchestrationService;
import com.biorad.csrag.interfaces.rest.answer.sender.MessageSender;
import com.biorad.csrag.interfaces.rest.answer.sender.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerComposerServiceUnitTest {

    @Mock
    private AnswerOrchestrationService orchestrationService;

    @Mock
    private AnswerDraftJpaRepository answerDraftRepository;

    @Mock
    private SendAttemptJpaRepository sendAttemptRepository;

    @Mock
    private MessageSender emailSender;

    private AnswerComposerService service;

    @BeforeEach
    void setUp() {
        service = new AnswerComposerService(
                orchestrationService,
                answerDraftRepository,
                sendAttemptRepository,
                List.of(emailSender)
        );
    }

    @Test
    void send_throws409_whenStatusIsNotApproved_and_logsRejectedAttempt() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        AnswerDraftJpaEntity entity = baseEntity(answerId, inquiryId, "DRAFT", "email");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.send(inquiryId, answerId, "sender-1", "email", "req-1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only approved answer can be sent");

        verify(emailSender, never()).send(any());

        ArgumentCaptor<SendAttemptJpaEntity> captor = ArgumentCaptor.forClass(SendAttemptJpaEntity.class);
        verify(sendAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("REJECTED_NOT_APPROVED");
    }

    @Test
    void send_returnsExisting_whenDuplicateRequestId_and_logsDuplicateBlocked() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        AnswerDraftJpaEntity entity = baseEntity(answerId, inquiryId, "APPROVED", "email");
        entity.markSent("sender-1", "email", "message-1", "dup-1");

        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)).thenReturn(Optional.of(entity));

        AnswerDraftResponse response = service.send(inquiryId, answerId, "sender-1", "email", "dup-1");

        assertThat(response.sendMessageId()).isEqualTo("message-1");
        verify(emailSender, never()).send(any());
        verify(answerDraftRepository, never()).save(any());

        ArgumentCaptor<SendAttemptJpaEntity> captor = ArgumentCaptor.forClass(SendAttemptJpaEntity.class);
        verify(sendAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("DUPLICATE_BLOCKED");
    }

    @Test
    void send_throws409_whenUnsupportedChannel() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        AnswerDraftJpaEntity entity = baseEntity(answerId, inquiryId, "APPROVED", "email");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)).thenReturn(Optional.of(entity));
        when(emailSender.supports("messenger")).thenReturn(false);

        assertThatThrownBy(() -> service.send(inquiryId, answerId, "sender-1", "messenger", "req-2"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Unsupported send channel");
    }

    @Test
    void send_marksSent_and_logsOutcome_whenApproved() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        AnswerDraftJpaEntity entity = baseEntity(answerId, inquiryId, "APPROVED", "email");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)).thenReturn(Optional.of(entity));
        when(answerDraftRepository.save(any(AnswerDraftJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(emailSender.supports("email")).thenReturn(true);
        when(emailSender.send(any())).thenReturn(new SendResult("mock", "mock-message-id"));

        AnswerDraftResponse response = service.send(inquiryId, answerId, "sender-1", "email", "req-3");

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.sendMessageId()).isEqualTo("mock-message-id");

        ArgumentCaptor<SendAttemptJpaEntity> captor = ArgumentCaptor.forClass(SendAttemptJpaEntity.class);
        verify(sendAttemptRepository).save(captor.capture());
        assertThat(captor.getValue().getOutcome()).isEqualTo("SENT");
    }

    @Test
    void approve_throws409_whenStatusIsSent() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();

        AnswerDraftJpaEntity entity = baseEntity(answerId, inquiryId, "APPROVED", "email");
        entity.markSent("sender-1", "email", "message-1", "req-1");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.approve(inquiryId, answerId, "approver-1", "approved"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only draft/reviewed answer can be approved");
    }

    private AnswerDraftJpaEntity baseEntity(UUID answerId, UUID inquiryId, String status, String channel) {
        return new AnswerDraftJpaEntity(
                answerId,
                inquiryId,
                1,
                "CONDITIONAL",
                0.5,
                "professional",
                channel,
                status,
                "draft",
                "",
                "",
                Instant.now(),
                Instant.now()
        );
    }
}
