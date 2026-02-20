package com.biorad.csrag.interfaces.rest.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class SseService {

    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(UUID inquiryId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);

        emitters.computeIfAbsent(inquiryId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> unregister(inquiryId, emitter));
        emitter.onTimeout(() -> unregister(inquiryId, emitter));
        emitter.onError(e -> unregister(inquiryId, emitter));

        // Send initial connection event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("inquiryId", inquiryId.toString(), "message", "SSE connection established")));
        } catch (IOException e) {
            unregister(inquiryId, emitter);
        }

        log.info("sse.register inquiryId={} activeEmitters={}", inquiryId, getEmitterCount(inquiryId));
        return emitter;
    }

    public void unregister(UUID inquiryId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(inquiryId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(inquiryId);
            }
        }
        log.debug("sse.unregister inquiryId={} remainingEmitters={}", inquiryId, getEmitterCount(inquiryId));
    }

    public void send(UUID inquiryId, String eventName, Object data) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(inquiryId);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException e) {
                unregister(inquiryId, emitter);
            }
        }
    }

    @Scheduled(fixedRate = 30_000)
    public void sendHeartbeat() {
        emitters.forEach((inquiryId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("heartbeat").data(Map.of("ts", System.currentTimeMillis())));
                } catch (IOException e) {
                    unregister(inquiryId, emitter);
                }
            }
        });
    }

    private int getEmitterCount(UUID inquiryId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(inquiryId);
        return list == null ? 0 : list.size();
    }
}
