package com.biorad.csrag.interfaces.rest.answer.sender;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
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
