package com.biorad.csrag.interfaces.rest.answer.sender;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class MockEmailSender implements MessageSender {

    @Override
    public boolean supports(String channel) {
        return "email".equalsIgnoreCase(channel);
    }

    @Override
    public SendResult send(SendCommand command) {
        return new SendResult("mock-email", "email-" + UUID.randomUUID());
    }
}
