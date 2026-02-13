package com.biorad.csrag.interfaces.rest.answer.sender;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class MockMessengerSender implements MessageSender {

    @Override
    public boolean supports(String channel) {
        return "messenger".equalsIgnoreCase(channel);
    }

    @Override
    public SendResult send(SendCommand command) {
        return new SendResult("mock-messenger", "msg-" + UUID.randomUUID());
    }
}
