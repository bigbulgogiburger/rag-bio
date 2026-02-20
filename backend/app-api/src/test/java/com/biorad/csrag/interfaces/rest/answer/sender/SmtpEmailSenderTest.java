package com.biorad.csrag.interfaces.rest.answer.sender;

import com.biorad.csrag.common.exception.ExternalServiceException;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SmtpEmailSenderTest {

    @Mock private JavaMailSender mailSender;
    @Mock private TemplateEngine templateEngine;
    @Mock private AnswerDraftJpaRepository answerDraftRepository;
    @Mock private MimeMessage mimeMessage;

    private SmtpEmailSender sender;

    @BeforeEach
    void setUp() {
        sender = new SmtpEmailSender(
                mailSender, templateEngine, answerDraftRepository,
                "noreply@biorad.com", "Bio-Rad CS", 2
        );
    }

    @Test
    void supports_email_returnsTrue() {
        assertThat(sender.supports("email")).isTrue();
        assertThat(sender.supports("EMAIL")).isTrue();
    }

    @Test
    void supports_nonEmail_returnsFalse() {
        assertThat(sender.supports("messenger")).isFalse();
        assertThat(sender.supports("sms")).isFalse();
    }

    @Test
    void send_draftNotFound_throwsException() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        SendCommand command = new SendCommand(inquiryId, answerId, "email", "user@test.com", "draft");
        when(answerDraftRepository.findById(answerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sender.send(command))
                .isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void send_success_returnsSmtpResult() throws Exception {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        SendCommand command = new SendCommand(inquiryId, answerId, "email", "user@test.com", "draft");
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getVerdict()).thenReturn("SUPPORTED");
        when(draft.getConfidence()).thenReturn(0.9);
        when(draft.getTone()).thenReturn("professional");
        when(draft.getVersion()).thenReturn(1);
        when(draft.getDraft()).thenReturn("Draft content");
        when(draft.getCitations()).thenReturn("cite1 | cite2");
        when(draft.getRiskFlags()).thenReturn(null);
        when(answerDraftRepository.findById(answerId)).thenReturn(Optional.of(draft));
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>rendered</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mimeMessage.getMessageID()).thenReturn("<msg-123@biorad.com>");

        SendResult result = sender.send(command);

        assertThat(result.provider()).isEqualTo("smtp");
        assertThat(result.messageId()).isEqualTo("<msg-123@biorad.com>");
        verify(mailSender).send(mimeMessage);
    }

    @Test
    void send_mailFailsAllRetries_throwsException() throws Exception {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        SendCommand command = new SendCommand(inquiryId, answerId, "email", "user@test.com", "draft");
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getVerdict()).thenReturn("SUPPORTED");
        when(draft.getConfidence()).thenReturn(0.9);
        when(draft.getTone()).thenReturn("professional");
        when(draft.getVersion()).thenReturn(1);
        when(draft.getDraft()).thenReturn("Draft");
        when(draft.getCitations()).thenReturn(null);
        when(draft.getRiskFlags()).thenReturn("");
        when(answerDraftRepository.findById(answerId)).thenReturn(Optional.of(draft));
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>body</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP timeout")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> sender.send(command))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessageContaining("failed after 2 attempts");

        verify(mailSender, times(2)).send(any(MimeMessage.class));
    }

    @Test
    void renderTemplate_handlesBlankCitationsAndRiskFlags() {
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getVerdict()).thenReturn("SUPPORTED");
        when(draft.getConfidence()).thenReturn(0.85);
        when(draft.getTone()).thenReturn("brief");
        when(draft.getVersion()).thenReturn(1);
        when(draft.getDraft()).thenReturn("Draft text");
        when(draft.getCitations()).thenReturn("");
        when(draft.getRiskFlags()).thenReturn("");
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>result</html>");

        String html = sender.renderTemplate(draft, UUID.randomUUID());

        assertThat(html).isEqualTo("<html>result</html>");
    }

    @Test
    void send_nullMessageId_generatesFallback() throws Exception {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        SendCommand command = new SendCommand(inquiryId, answerId, "email", "user@test.com", "draft");
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getVerdict()).thenReturn("SUPPORTED");
        when(draft.getConfidence()).thenReturn(0.9);
        when(draft.getTone()).thenReturn("professional");
        when(draft.getVersion()).thenReturn(1);
        when(draft.getDraft()).thenReturn("Draft");
        when(draft.getCitations()).thenReturn(null);
        when(draft.getRiskFlags()).thenReturn(null);
        when(answerDraftRepository.findById(answerId)).thenReturn(Optional.of(draft));
        when(templateEngine.process(anyString(), any(Context.class))).thenReturn("<html>body</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(mimeMessage.getMessageID()).thenReturn(null);

        SendResult result = sender.send(command);

        assertThat(result.messageId()).startsWith("smtp-");
    }
}
