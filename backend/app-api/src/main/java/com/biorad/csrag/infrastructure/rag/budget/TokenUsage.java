package com.biorad.csrag.infrastructure.rag.budget;

import java.util.Map;

/**
 * 파이프라인 단계별 토큰 사용량을 나타내는 불변 값 객체.
 */
public record TokenUsage(
        String stepName,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        String modelId,
        double estimatedCostUsd
) {

    /**
     * 팩토리 메서드: stepName, prompt/completion 토큰 수, 모델 ID로 TokenUsage를 생성한다.
     */
    public static TokenUsage of(String stepName, int prompt, int completion, String modelId) {
        int total = prompt + completion;
        double cost = estimateCost(modelId, prompt, completion);
        return new TokenUsage(stepName, prompt, completion, total, modelId, cost);
    }

    /**
     * 모델별 1M 토큰당 USD 비용 (input, output).
     * PipelineTraceContext.PipelineTrace 의 COST_PER_MILLION 과 동일한 추정치.
     */
    private static final Map<String, double[]> COST_PER_MILLION = Map.of(
            "gpt-5-mini",             new double[]{0.40, 1.60},
            "gpt-5-nano",             new double[]{0.10, 0.40},
            "gpt-4.1-mini",           new double[]{0.40, 1.60},
            "gpt-4.1-nano",           new double[]{0.10, 0.40},
            "gpt-4o-mini",            new double[]{0.15, 0.60},
            "gpt-4o",                 new double[]{2.50, 10.00},
            "text-embedding-3-large", new double[]{0.13, 0.00},
            "text-embedding-3-small", new double[]{0.02, 0.00}
    );

    /** 알 수 없는 모델에 적용할 기본 비용 (보수적 추정). */
    private static final double[] DEFAULT_COST = {0.50, 2.00};

    private static double estimateCost(String modelId, int prompt, int completion) {
        double[] rates = resolveCost(modelId);
        return (prompt / 1_000_000.0) * rates[0]
                + (completion / 1_000_000.0) * rates[1];
    }

    private static double[] resolveCost(String model) {
        if (model == null) {
            return DEFAULT_COST;
        }
        String lower = model.toLowerCase();
        double[] exact = COST_PER_MILLION.get(lower);
        if (exact != null) {
            return exact;
        }
        for (var entry : COST_PER_MILLION.entrySet()) {
            if (lower.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return DEFAULT_COST;
    }
}
