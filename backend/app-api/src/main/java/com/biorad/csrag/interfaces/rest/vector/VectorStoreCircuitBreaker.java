package com.biorad.csrag.interfaces.rest.vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Vector DB용 수동 Circuit Breaker 구현.
 * <p>
 * 외부 라이브러리(Resilience4j 등) 없이 3-state (CLOSED → OPEN → HALF_OPEN) 패턴을 구현한다.
 * Vector DB(Qdrant)가 다운되면 자동으로 keyword-only 검색으로 graceful degradation.
 * </p>
 *
 * <ul>
 *   <li>CLOSED: 정상 상태. 실패 시 카운터 증가.</li>
 *   <li>OPEN: failureThreshold 도달 후 차단. fallback 반환.</li>
 *   <li>HALF_OPEN: resetTimeout 경과 후 시험 요청 1건 통과.</li>
 * </ul>
 */
@Component
public class VectorStoreCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreCircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final int resetTimeoutSeconds;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);

    public VectorStoreCircuitBreaker(
            @Value("${rag.circuit-breaker.failure-threshold:3}") int failureThreshold,
            @Value("${rag.circuit-breaker.reset-timeout-seconds:30}") int resetTimeoutSeconds
    ) {
        this.failureThreshold = failureThreshold;
        this.resetTimeoutSeconds = resetTimeoutSeconds;
    }

    /**
     * Circuit breaker로 보호된 실행.
     *
     * @param operation 벡터 DB 작업 (보호 대상)
     * @param fallback  circuit OPEN 시 대체 값 공급자
     * @param <T>       반환 타입
     * @return 작업 결과 또는 fallback 값
     */
    public <T> T execute(Supplier<T> operation, Supplier<T> fallback) {
        State currentState = getEffectiveState();

        switch (currentState) {
            case OPEN:
                log.warn("Circuit breaker OPEN — using fallback (keyword-only search)");
                return fallback.get();

            case HALF_OPEN:
                log.info("Circuit breaker HALF_OPEN — testing vector DB connection");
                try {
                    T result = operation.get();
                    onSuccess();
                    return result;
                } catch (Exception e) {
                    onFailure(e);
                    return fallback.get();
                }

            case CLOSED:
            default:
                try {
                    T result = operation.get();
                    onSuccess();
                    return result;
                } catch (Exception e) {
                    onFailure(e);
                    // threshold 도달하여 OPEN으로 전환된 경우 fallback 반환
                    if (state.get() == State.OPEN) {
                        return fallback.get();
                    }
                    throw e; // 아직 CLOSED 상태 — 예외 전파
                }
        }
    }

    /**
     * 현재 유효 상태를 반환한다.
     * OPEN 상태에서 resetTimeout이 경과하면 HALF_OPEN으로 전이.
     */
    State getEffectiveState() {
        if (state.get() == State.OPEN) {
            long elapsed = System.currentTimeMillis() - lastFailureTime.get();
            if (elapsed >= resetTimeoutSeconds * 1000L) {
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                return State.HALF_OPEN;
            }
        }
        return state.get();
    }

    private void onSuccess() {
        failureCount.set(0);
        state.set(State.CLOSED);
    }

    private void onFailure(Exception e) {
        lastFailureTime.set(System.currentTimeMillis());
        int failures = failureCount.incrementAndGet();
        log.warn("Vector DB failure #{}: {}", failures, e.getMessage());
        if (failures >= failureThreshold) {
            state.set(State.OPEN);
            log.error("Circuit breaker tripped to OPEN after {} consecutive failures", failures);
        }
    }

    // ── 모니터링용 메서드 ──

    /** 현재 유효 상태 (OPEN → timeout 경과 시 HALF_OPEN 자동 전이 반영) */
    public State getState() {
        return getEffectiveState();
    }

    /** 현재 연속 실패 횟수 */
    public int getFailureCount() {
        return failureCount.get();
    }

    /** 비정상 상태 여부 (OPEN 또는 HALF_OPEN) */
    public boolean isDegraded() {
        return getEffectiveState() != State.CLOSED;
    }
}
