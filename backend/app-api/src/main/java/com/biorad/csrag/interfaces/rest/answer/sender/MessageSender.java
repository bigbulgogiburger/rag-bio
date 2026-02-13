package com.biorad.csrag.interfaces.rest.answer.sender;

public interface MessageSender {
    boolean supports(String channel);
    SendResult send(SendCommand command);
}
