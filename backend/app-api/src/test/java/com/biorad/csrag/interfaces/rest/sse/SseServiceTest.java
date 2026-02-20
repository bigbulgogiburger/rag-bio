package com.biorad.csrag.interfaces.rest.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SseServiceTest {

    private SseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new SseService();
    }

    @Test
    void register_returnsNonNullEmitter() {
        UUID inquiryId = UUID.randomUUID();
        SseEmitter emitter = sseService.register(inquiryId);
        assertThat(emitter).isNotNull();
    }

    @Test
    void unregister_removesEmitter() {
        UUID inquiryId = UUID.randomUUID();
        SseEmitter emitter = sseService.register(inquiryId);
        sseService.unregister(inquiryId, emitter);
        assertThatCode(() -> sseService.send(inquiryId, "test", "data")).doesNotThrowAnyException();
    }

    @Test
    void unregister_nonExistentInquiry_doesNotThrow() {
        assertThatCode(() -> sseService.unregister(UUID.randomUUID(), new SseEmitter())).doesNotThrowAnyException();
    }

    @Test
    void send_noEmitters_doesNotThrow() {
        assertThatCode(() -> sseService.send(UUID.randomUUID(), "test", "data")).doesNotThrowAnyException();
    }

    @Test
    void send_afterRegister_sendsEvent() {
        UUID inquiryId = UUID.randomUUID();
        sseService.register(inquiryId);
        assertThatCode(() -> sseService.send(inquiryId, "status", Map.of("key", "value"))).doesNotThrowAnyException();
    }

    @Test
    void sendHeartbeat_noEmitters_doesNotThrow() {
        assertThatCode(() -> sseService.sendHeartbeat()).doesNotThrowAnyException();
    }

    @Test
    void sendHeartbeat_withEmitter_sendsHeartbeat() {
        UUID inquiryId = UUID.randomUUID();
        sseService.register(inquiryId);
        assertThatCode(() -> sseService.sendHeartbeat()).doesNotThrowAnyException();
    }

    @Test
    void multipleRegistrations_sameInquiry() {
        UUID inquiryId = UUID.randomUUID();
        SseEmitter emitter1 = sseService.register(inquiryId);
        SseEmitter emitter2 = sseService.register(inquiryId);
        assertThat(emitter1).isNotSameAs(emitter2);

        sseService.unregister(inquiryId, emitter1);
        assertThatCode(() -> sseService.send(inquiryId, "test", "data")).doesNotThrowAnyException();
    }

    @Test
    void unregisterAll_removesInquiry() {
        UUID inquiryId = UUID.randomUUID();
        SseEmitter emitter = sseService.register(inquiryId);
        sseService.unregister(inquiryId, emitter);
        assertThatCode(() -> sseService.send(inquiryId, "test", "data")).doesNotThrowAnyException();
    }
}
