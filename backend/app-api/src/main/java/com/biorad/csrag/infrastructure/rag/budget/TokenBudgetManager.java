package com.biorad.csrag.infrastructure.rag.budget;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 파이프라인 요청별 토큰 예산 관리자.
 *
 * <p>Spring 빈이 아니며, AnswerOrchestrationService 에서 요청마다 새 인스턴스를 생성한다.
 * 필수 단계(DECOMPOSE, RETRIEVE, VERIFY, COMPOSE)는 예산 초과 시에도 항상 진행하고,
 * 선택 단계(CRITIC, SELF_REVIEW, ADAPTIVE_RETRIEVE, MULTI_HOP, RERANKING)는
 * 예산이 부족하면 건너뛴다.
 */
public class TokenBudgetManager {

    private final int maxBudget;
    private final AtomicInteger consumed = new AtomicInteger(0);
    private final Map<String, TokenUsage> stepUsages = new ConcurrentHashMap<>();

    /** 예산 부족 시 건너뛸 수 있는 단계 (스킵 우선순위 순서). */
    private static final List<String> SKIPPABLE_STEPS = List.of(
            "CRITIC", "SELF_REVIEW", "ADAPTIVE_RETRIEVE", "MULTI_HOP", "RERANKING"
    );

    /** 예산과 무관하게 반드시 실행해야 하는 단계. */
    private static final Set<String> MANDATORY_STEPS = Set.of(
            "DECOMPOSE", "RETRIEVE", "VERIFY", "COMPOSE"
    );

    public TokenBudgetManager(int maxBudget) {
        this.maxBudget = maxBudget;
    }

    /**
     * 해당 단계를 진행할 수 있는지 판단한다.
     *
     * @param stepName        파이프라인 단계 이름
     * @param estimatedTokens 해당 단계에서 예상되는 토큰 사용량
     * @return 필수 단계이면 항상 {@code true}, 선택 단계이면 예산 범위 내일 때만 {@code true}
     */
    public boolean canProceed(String stepName, int estimatedTokens) {
        if (MANDATORY_STEPS.contains(stepName)) {
            return true;
        }
        return consumed.get() + estimatedTokens <= maxBudget;
    }

    /**
     * 단계 완료 후 실제 토큰 사용량을 기록한다.
     */
    public void recordUsage(TokenUsage usage) {
        consumed.addAndGet(usage.totalTokens());
        stepUsages.put(usage.stepName(), usage);
    }

    public int getRemainingBudget() {
        return Math.max(0, maxBudget - consumed.get());
    }

    public int getConsumedTokens() {
        return consumed.get();
    }

    public Map<String, TokenUsage> getStepUsages() {
        return Collections.unmodifiableMap(stepUsages);
    }

    public boolean isOverBudget() {
        return consumed.get() > maxBudget;
    }

    /**
     * 로깅/응답용 예산 요약 정보를 반환한다.
     */
    public Map<String, Object> getSummary() {
        return Map.of(
                "maxBudget", maxBudget,
                "consumed", consumed.get(),
                "remaining", getRemainingBudget(),
                "overBudget", isOverBudget(),
                "steps", Collections.unmodifiableMap(stepUsages)
        );
    }
}
