package com.biorad.csrag.interfaces.rest.answer.sender;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSenderTest {

    @Test
    void mockEmailSender_supportsEmail() {
        MockEmailSender sender = new MockEmailSender();
        assertThat(sender.supports("email")).isTrue();
        assertThat(sender.supports("EMAIL")).isTrue();
        assertThat(sender.supports("messenger")).isFalse();
    }

    @Test
    void mockEmailSender_send_returnsMockResult() {
        MockEmailSender sender = new MockEmailSender();
        SendResult result = sender.send(new SendCommand(UUID.randomUUID(), UUID.randomUUID(), "email", "user@test.com", "draft"));
        assertThat(result.provider()).isEqualTo("mock-email");
        assertThat(result.messageId()).startsWith("email-");
    }

    @Test
    void mockMessengerSender_supportsMessenger() {
        MockMessengerSender sender = new MockMessengerSender();
        assertThat(sender.supports("messenger")).isTrue();
        assertThat(sender.supports("MESSENGER")).isTrue();
        assertThat(sender.supports("email")).isFalse();
    }

    @Test
    void mockMessengerSender_send_returnsMockResult() {
        MockMessengerSender sender = new MockMessengerSender();
        SendResult result = sender.send(new SendCommand(UUID.randomUUID(), UUID.randomUUID(), "messenger", "user", "draft"));
        assertThat(result.provider()).isEqualTo("mock-messenger");
        assertThat(result.messageId()).startsWith("msg-");
    }

    @Test
    void sendCommand_recordAccessors() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        SendCommand cmd = new SendCommand(inquiryId, answerId, "email", "actor@test.com", "the draft");

        assertThat(cmd.inquiryId()).isEqualTo(inquiryId);
        assertThat(cmd.answerId()).isEqualTo(answerId);
        assertThat(cmd.channel()).isEqualTo("email");
        assertThat(cmd.actor()).isEqualTo("actor@test.com");
        assertThat(cmd.draft()).isEqualTo("the draft");
    }

    @Test
    void sendResult_recordAccessors() {
        SendResult result = new SendResult("provider", "msg-123");
        assertThat(result.provider()).isEqualTo("provider");
        assertThat(result.messageId()).isEqualTo("msg-123");
    }
}
