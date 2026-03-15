package com.biorad.csrag.infrastructure.rag.budget;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBudgetManagerTest {

    // ── 필수 단계는 예산 초과해도 항상 진행 ──────────────────────────

    @Test
    void mandatorySteps_alwaysProceed_evenWhenOverBudget() {
        TokenBudgetManager manager = new TokenBudgetManager(100);
        // 예산(100)을 초과하는 사용량 기록
        manager.recordUsage(TokenUsage.of("RETRIEVE", 80, 80, "gpt-5-mini"));

        assertThat(manager.isOverBudget()).isTrue();
        // MANDATORY 단계: 예산 초과여도 true
        assertThat(manager.canProceed("DECOMPOSE", 5000)).isTrue();
        assertThat(manager.canProceed("RETRIEVE", 5000)).isTrue();
        assertThat(manager.canProceed("VERIFY", 5000)).isTrue();
        assertThat(manager.canProceed("COMPOSE", 5000)).isTrue();
    }

    // ── 선택 단계는 예산 초과 시 차단 ──────────────────────────────

    @Test
    void skippableSteps_blocked_whenOverBudget() {
        TokenBudgetManager manager = new TokenBudgetManager(10000);
        manager.recordUsage(TokenUsage.of("RETRIEVE", 4000, 3000, "gpt-5-mini"));

        // 소모: 7000, 남은 예산: 3000
        assertThat(manager.canProceed("CRITIC", 5000)).isFalse();
        assertThat(manager.canProceed("SELF_REVIEW", 4000)).isFalse();
        assertThat(manager.canProceed("ADAPTIVE_RETRIEVE", 4000)).isFalse();
        assertThat(manager.canProceed("MULTI_HOP", 4000)).isFalse();
        assertThat(manager.canProceed("RERANKING", 3001)).isFalse();
    }

    @Test
    void skippableSteps_allowed_whenWithinBudget() {
        TokenBudgetManager manager = new TokenBudgetManager(25000);
        manager.recordUsage(TokenUsage.of("RETRIEVE", 2000, 1000, "gpt-5-mini"));

        // 소모: 3000, 남은 예산: 22000
        assertThat(manager.canProceed("CRITIC", 5000)).isTrue();
        assertThat(manager.canProceed("SELF_REVIEW", 4000)).isTrue();
        assertThat(manager.canProceed("ADAPTIVE_RETRIEVE", 4000)).isTrue();
    }

    @Test
    void skippableStep_exactBoundary_allowed() {
        TokenBudgetManager manager = new TokenBudgetManager(10000);
        manager.recordUsage(TokenUsage.of("RETRIEVE", 3000, 2000, "gpt-5-mini"));

        // 소모: 5000, 남은 예산: 5000
        assertThat(manager.canProceed("CRITIC", 5000)).isTrue();
        assertThat(manager.canProceed("CRITIC", 5001)).isFalse();
    }

    // ── recordUsage 및 getSummary ──────────────────────────────

    @Test
    void recordUsage_updatesConsumedAndStepUsages() {
        TokenBudgetManager manager = new TokenBudgetManager(25000);

        TokenUsage usage1 = TokenUsage.of("DECOMPOSE", 500, 200, "gpt-5-nano");
        TokenUsage usage2 = TokenUsage.of("RETRIEVE", 1000, 500, "gpt-5-mini");
        manager.recordUsage(usage1);
        manager.recordUsage(usage2);

        assertThat(manager.getConsumedTokens()).isEqualTo(700 + 1500);
        assertThat(manager.getRemainingBudget()).isEqualTo(25000 - 2200);
        assertThat(manager.isOverBudget()).isFalse();

        Map<String, TokenUsage> usages = manager.getStepUsages();
        assertThat(usages).containsKeys("DECOMPOSE", "RETRIEVE");
        assertThat(usages.get("DECOMPOSE").totalTokens()).isEqualTo(700);
        assertThat(usages.get("RETRIEVE").totalTokens()).isEqualTo(1500);
    }

    @Test
    void getSummary_containsAllFields() {
        TokenBudgetManager manager = new TokenBudgetManager(10000);
        manager.recordUsage(TokenUsage.of("COMPOSE", 3000, 2000, "gpt-5-mini"));

        Map<String, Object> summary = manager.getSummary();

        assertThat(summary.get("maxBudget")).isEqualTo(10000);
        assertThat(summary.get("consumed")).isEqualTo(5000);
        assertThat(summary.get("remaining")).isEqualTo(5000);
        assertThat(summary.get("overBudget")).isEqualTo(false);
        assertThat(summary.get("steps")).isInstanceOf(Map.class);
    }

    @Test
    void getRemainingBudget_neverNegative() {
        TokenBudgetManager manager = new TokenBudgetManager(100);
        manager.recordUsage(TokenUsage.of("COMPOSE", 500, 500, "gpt-5-mini"));

        assertThat(manager.isOverBudget()).isTrue();
        assertThat(manager.getRemainingBudget()).isEqualTo(0);
    }

    // ── TokenUsage 팩토리 ──────────────────────────────────────

    @Test
    void tokenUsage_of_calculatesTotalAndCost() {
        TokenUsage usage = TokenUsage.of("COMPOSE", 1_000_000, 500_000, "gpt-5-mini");

        assertThat(usage.totalTokens()).isEqualTo(1_500_000);
        // gpt-5-mini: input $0.40/1M, output $1.60/1M
        // cost = (1M * 0.40) + (0.5M * 1.60) = 0.40 + 0.80 = 1.20
        assertThat(usage.estimatedCostUsd()).isCloseTo(1.20, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void tokenUsage_unknownModel_usesDefaultCost() {
        TokenUsage usage = TokenUsage.of("COMPOSE", 1_000_000, 1_000_000, "unknown-model-xyz");

        // DEFAULT_COST: input $0.50/1M, output $2.00/1M
        // cost = 0.50 + 2.00 = 2.50
        assertThat(usage.estimatedCostUsd()).isCloseTo(2.50, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    void tokenUsage_nullModel_usesDefaultCost() {
        TokenUsage usage = TokenUsage.of("COMPOSE", 1_000_000, 1_000_000, null);

        assertThat(usage.estimatedCostUsd()).isCloseTo(2.50, org.assertj.core.data.Offset.offset(0.01));
    }

    // ── 동시성 안전성 (AtomicInteger) ──────────────────────────

    @Test
    void concurrentRecording_isThreadSafe() throws InterruptedException {
        TokenBudgetManager manager = new TokenBudgetManager(Integer.MAX_VALUE);
        int threadCount = 10;
        int usagePerThread = 100;
        int tokensPerUsage = 100; // 50 prompt + 50 completion

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Exception> errors = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < usagePerThread; i++) {
                        manager.recordUsage(TokenUsage.of(
                                "STEP_" + threadIdx + "_" + i, 50, 50, "gpt-5-mini"));
                    }
                } catch (Exception e) {
                    synchronized (errors) {
                        errors.add(e);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(errors).isEmpty();
        // 총 소모량: 10 threads * 100 usages * 100 tokens = 100,000
        assertThat(manager.getConsumedTokens()).isEqualTo(threadCount * usagePerThread * tokensPerUsage);
    }

    // ── 초기 상태 ──────────────────────────────────────────────

    @Test
    void newManager_hasZeroConsumed() {
        TokenBudgetManager manager = new TokenBudgetManager(25000);

        assertThat(manager.getConsumedTokens()).isEqualTo(0);
        assertThat(manager.getRemainingBudget()).isEqualTo(25000);
        assertThat(manager.isOverBudget()).isFalse();
        assertThat(manager.getStepUsages()).isEmpty();
    }

    @Test
    void stepUsages_isUnmodifiable() {
        TokenBudgetManager manager = new TokenBudgetManager(25000);
        Map<String, TokenUsage> usages = manager.getStepUsages();

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> usages.put("HACK", TokenUsage.of("HACK", 1, 1, "x"))
        );
    }
}
