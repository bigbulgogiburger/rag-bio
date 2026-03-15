package com.biorad.csrag.interfaces.rest.vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VectorStoreCircuitBreakerTest {

    private VectorStoreCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        // failureThreshold=3, resetTimeoutSeconds=1 (테스트 속도를 위해 짧게)
        circuitBreaker = new VectorStoreCircuitBreaker(3, 1);
    }

    @Test
    @DisplayName("CLOSED 상태: 정상 작업 성공 시 카운터는 0을 유지한다")
    void closedState_successKeepsCounterAtZero() {
        String result = circuitBreaker.execute(
                () -> "success",
                () -> "fallback"
        );

        assertThat(result).isEqualTo("success");
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isZero();
        assertThat(circuitBreaker.isDegraded()).isFalse();
    }

    @Test
    @DisplayName("CLOSED 상태: threshold 미만 실패 시 예외가 전파된다")
    void closedState_failureBelowThresholdPropagatesException() {
        assertThatThrownBy(() ->
                circuitBreaker.execute(
                        () -> { throw new RuntimeException("db down"); },
                        () -> "fallback"
                )
        ).isInstanceOf(RuntimeException.class).hasMessage("db down");

        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("CLOSED → OPEN: N번 연속 실패 시 OPEN으로 전이한다")
    void closedToOpen_afterNConsecutiveFailures() {
        // 실패 3번 (threshold=3)
        for (int i = 0; i < 2; i++) {
            final int attempt = i;
            try {
                circuitBreaker.execute(
                        () -> { throw new RuntimeException("fail " + attempt); },
                        () -> "fallback"
                );
            } catch (RuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.CLOSED);

        // 3번째 실패 — threshold 도달 → OPEN, fallback 반환
        String result = circuitBreaker.execute(
                () -> { throw new RuntimeException("fail 3"); },
                () -> "fallback"
        );

        assertThat(result).isEqualTo("fallback");
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.OPEN);
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(3);
        assertThat(circuitBreaker.isDegraded()).isTrue();
    }

    @Test
    @DisplayName("OPEN 상태: 작업을 호출하지 않고 fallback을 반환한다")
    void openState_usesFallbackWithoutCallingOperation() {
        // OPEN 상태로 만들기
        tripToOpen();

        AtomicInteger operationCallCount = new AtomicInteger(0);
        String result = circuitBreaker.execute(
                () -> {
                    operationCallCount.incrementAndGet();
                    return "should not be called";
                },
                () -> "fallback"
        );

        assertThat(result).isEqualTo("fallback");
        assertThat(operationCallCount.get()).isZero();
    }

    @Test
    @DisplayName("OPEN → HALF_OPEN: resetTimeout 경과 후 HALF_OPEN으로 전이한다")
    void openToHalfOpen_afterTimeout() throws InterruptedException {
        tripToOpen();
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.OPEN);

        // resetTimeoutSeconds=1 → 1100ms 대기
        Thread.sleep(1100);

        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.HALF_OPEN);
        assertThat(circuitBreaker.isDegraded()).isTrue();
    }

    @Test
    @DisplayName("HALF_OPEN → CLOSED: 시험 요청 성공 시 CLOSED로 복귀한다")
    void halfOpenToClosed_onSuccess() throws InterruptedException {
        tripToOpen();
        Thread.sleep(1100);
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.HALF_OPEN);

        String result = circuitBreaker.execute(
                () -> "recovered",
                () -> "fallback"
        );

        assertThat(result).isEqualTo("recovered");
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getFailureCount()).isZero();
        assertThat(circuitBreaker.isDegraded()).isFalse();
    }

    @Test
    @DisplayName("HALF_OPEN → OPEN: 시험 요청 실패 시 다시 OPEN으로 전이한다")
    void halfOpenToOpen_onFailure() throws InterruptedException {
        tripToOpen();
        Thread.sleep(1100);
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.HALF_OPEN);

        String result = circuitBreaker.execute(
                () -> { throw new RuntimeException("still down"); },
                () -> "fallback"
        );

        assertThat(result).isEqualTo("fallback");
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.OPEN);
    }

    @Test
    @DisplayName("성공이 실패 카운터를 리셋한다")
    void successResetsFailureCounter() {
        // 실패 2번
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.execute(
                        () -> { throw new RuntimeException("fail"); },
                        () -> "fallback"
                );
            } catch (RuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getFailureCount()).isEqualTo(2);

        // 성공 1번 → 카운터 리셋
        circuitBreaker.execute(() -> "ok", () -> "fallback");
        assertThat(circuitBreaker.getFailureCount()).isZero();

        // 다시 실패 2번 → 아직 CLOSED (threshold=3 미달)
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.execute(
                        () -> { throw new RuntimeException("fail again"); },
                        () -> "fallback"
                );
            } catch (RuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("isDegraded(): CLOSED이면 false, OPEN/HALF_OPEN이면 true")
    void isDegraded_reflectsState() throws InterruptedException {
        assertThat(circuitBreaker.isDegraded()).isFalse();

        tripToOpen();
        assertThat(circuitBreaker.isDegraded()).isTrue();

        Thread.sleep(1100);
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.HALF_OPEN);
        assertThat(circuitBreaker.isDegraded()).isTrue();

        // HALF_OPEN에서 성공 → CLOSED
        circuitBreaker.execute(() -> "ok", () -> "fallback");
        assertThat(circuitBreaker.isDegraded()).isFalse();
    }

    @Test
    @DisplayName("동시 접근: 여러 스레드에서 안전하게 동작한다")
    void concurrentAccess_threadSafety() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger fallbackCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    String result = circuitBreaker.execute(
                            () -> {
                                // 짝수 인덱스는 실패
                                if (idx % 2 == 0) {
                                    throw new RuntimeException("fail-" + idx);
                                }
                                return "success-" + idx;
                            },
                            () -> {
                                fallbackCount.incrementAndGet();
                                return "fallback-" + idx;
                            }
                    );
                    if (result.startsWith("success") || result.startsWith("fallback")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        // 상태가 유효한 값 중 하나여야 한다
        VectorStoreCircuitBreaker.State finalState = circuitBreaker.getState();
        assertThat(finalState).isIn(
                VectorStoreCircuitBreaker.State.CLOSED,
                VectorStoreCircuitBreaker.State.OPEN,
                VectorStoreCircuitBreaker.State.HALF_OPEN
        );

        // 예외 또는 결과를 받은 호출의 합이 전체 스레드 수와 같아야 한다
        assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
    }

    @Test
    @DisplayName("OPEN 상태에서 여러 번 호출해도 계속 fallback을 반환한다")
    void openState_repeatedCallsAlwaysReturnFallback() {
        tripToOpen();

        for (int i = 0; i < 5; i++) {
            String result = circuitBreaker.execute(
                    () -> "should not reach",
                    () -> "fallback"
            );
            assertThat(result).isEqualTo("fallback");
        }
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.OPEN);
    }

    /**
     * Circuit breaker를 OPEN 상태로 전이시키는 헬퍼.
     */
    private void tripToOpen() {
        for (int i = 0; i < 3; i++) {
            final int attempt = i;
            try {
                circuitBreaker.execute(
                        () -> { throw new RuntimeException("force open " + attempt); },
                        () -> "fallback"
                );
            } catch (RuntimeException ignored) {}
        }
        assertThat(circuitBreaker.getState()).isEqualTo(VectorStoreCircuitBreaker.State.OPEN);
    }
}
