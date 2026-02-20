package com.biorad.csrag.interfaces.rest.answer.sender;

import com.biorad.csrag.common.exception.ExternalServiceException;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;
import java.util.UUID;

@Component
@Primary
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "true")
public class SmtpEmailSender implements MessageSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final AnswerDraftJpaRepository answerDraftRepository;
    private final String fromAddress;
    private final String fromName;
    private final int maxRetries;

    public SmtpEmailSender(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            AnswerDraftJpaRepository answerDraftRepository,
            @Value("${app.mail.from:noreply@biorad.com}") String fromAddress,
            @Value("${app.mail.from-name:Bio-Rad CS 기술지원}") String fromName,
            @Value("${app.mail.max-retries:3}") int maxRetries
    ) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.answerDraftRepository = answerDraftRepository;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.maxRetries = maxRetries;
    }

    @Override
    public boolean supports(String channel) {
        return "email".equalsIgnoreCase(channel);
    }

    @Override
    public SendResult send(SendCommand command) {
        AnswerDraftJpaEntity draft = answerDraftRepository.findById(command.answerId())
                .orElseThrow(() -> new ExternalServiceException("SMTP", "Answer draft not found for email rendering"));

        String htmlBody = renderTemplate(draft, command.inquiryId());
        String subject = buildSubject(command.inquiryId());

        String messageId = sendWithRetry(command.actor(), subject, htmlBody);

        return new SendResult("smtp", messageId);
    }

    /**
     * Thymeleaf 템플릿으로 이메일 HTML 렌더링 (preview 기능에서도 사용)
     */
    public String renderTemplate(AnswerDraftJpaEntity draft, UUID inquiryId) {
        Context ctx = new Context();
        ctx.setVariable("inquiryId", inquiryId.toString());
        ctx.setVariable("verdict", draft.getVerdict());
        ctx.setVariable("confidence", draft.getConfidence());
        ctx.setVariable("tone", draft.getTone());
        ctx.setVariable("version", draft.getVersion());
        ctx.setVariable("draft", draft.getDraft());

        List<String> citations = draft.getCitations() == null || draft.getCitations().isBlank()
                ? List.of()
                : List.of(draft.getCitations().split("\\s*\\|\\s*"));
        ctx.setVariable("citations", citations);

        List<String> riskFlags = draft.getRiskFlags() == null || draft.getRiskFlags().isBlank()
                ? List.of()
                : List.of(draft.getRiskFlags().split("\\s*,\\s*"));
        ctx.setVariable("riskFlags", riskFlags);

        return templateEngine.process("email/answer-draft", ctx);
    }

    private String sendWithRetry(String recipientEmail, String subject, String htmlBody) {
        int attempt = 0;
        long backoffMs = 1000;

        while (true) {
            attempt++;
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(fromAddress, fromName);
                helper.setTo(recipientEmail);
                helper.setSubject(subject);
                helper.setText(htmlBody, true);

                mailSender.send(message);
                String messageId = message.getMessageID();
                log.info("smtp.send.success to={} attempt={} messageId={}", recipientEmail, attempt, messageId);
                return messageId != null ? messageId : "smtp-" + UUID.randomUUID();

            } catch (MailException | MessagingException | java.io.UnsupportedEncodingException e) {
                log.warn("smtp.send.failed to={} attempt={}/{} error={}", recipientEmail, attempt, maxRetries, e.getMessage());

                if (attempt >= maxRetries) {
                    throw new ExternalServiceException("SMTP",
                            "Email delivery failed after " + maxRetries + " attempts: " + e.getMessage());
                }

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ExternalServiceException("SMTP", "Email delivery interrupted");
                }
                backoffMs *= 2; // exponential backoff
            }
        }
    }

    private String buildSubject(UUID inquiryId) {
        return "[Bio-Rad CS] 기술지원 답변 - " + inquiryId.toString().substring(0, 8);
    }
}
