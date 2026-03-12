package com.biorad.csrag.infrastructure.openai;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * ThreadLocal 기반 파이프라인 실행 추적.
 * 각 LLM 호출의 토큰 사용량을 누적하여 파이프라인 완료 시 합산.
 *
 * <p>사용 패턴:
 * <pre>
 *   PipelineTraceContext.start(inquiryId);
 *   try {
 *       // ... pipeline steps (각 step에서 recordLlmCall 호출)
 *   } finally {
 *       PipelineTrace trace = PipelineTraceContext.finish();
 *       traceLogger.logTrace(trace);
 *   }
 * </pre>
 */
public final class PipelineTraceContext {

    private static final ThreadLocal<PipelineTrace> CURRENT = new ThreadLocal<>();

    private PipelineTraceContext() {}

    /**
     * 새 파이프라인 추적을 시작한다. 이전 추적이 남아 있으면 덮어쓴다.
     */
    public static void start(String inquiryId) {
        CURRENT.set(new PipelineTrace(inquiryId, Instant.now()));
    }

    /**
     * 현재 파이프라인 추적에 LLM 호출 기록을 추가한다.
     * 추적이 시작되지 않은 상태에서 호출하면 아무 동작도 하지 않는다 (no-op).
     */
    public static void recordLlmCall(String step, String model,
                                     int inputTokens, int outputTokens,
                                     long latencyMs) {
        PipelineTrace trace = CURRENT.get();
        if (trace != null) {
            trace.addCall(new LlmCallRecord(
                    step, model, inputTokens, outputTokens, latencyMs, Instant.now()));
        }
    }

    /**
     * 파이프라인 추적을 종료하고 결과를 반환한다. ThreadLocal을 정리한다.
     *
     * @return 누적된 추적 결과, 또는 시작되지 않은 경우 {@code null}
     */
    public static PipelineTrace finish() {
        PipelineTrace trace = CURRENT.get();
        CURRENT.remove();
        return trace;
    }

    /**
     * 현재 진행 중인 파이프라인 추적을 반환한다.
     *
     * @return 현재 추적, 또는 시작되지 않은 경우 {@code null}
     */
    public static PipelineTrace current() {
        return CURRENT.get();
    }

    // ── Inner types ──────────────────────────────────────────────

    /**
     * 개별 LLM 호출 기록.
     */
    public record LlmCallRecord(
            String step,
            String model,
            int inputTokens,
            int outputTokens,
            long latencyMs,
            Instant timestamp
    ) {
        public int totalTokens() {
            return inputTokens + outputTokens;
        }
    }

    /**
     * 파이프라인 실행 전체 추적 데이터.
     */
    public static class PipelineTrace {

        private final String inquiryId;
        private final Instant startTime;
        private final List<LlmCallRecord> calls = new ArrayList<>();

        /**
         * 모델별 1M 토큰당 USD 비용 (input, output).
         * 추정치 기반 — 실제 과금과 차이가 있을 수 있음.
         */
        private static final Map<String, double[]> COST_PER_MILLION = Map.of(
                "gpt-5-mini",                new double[]{0.40, 1.60},
                "gpt-5-nano",                new double[]{0.10, 0.40},
                "gpt-4.1-mini",              new double[]{0.40, 1.60},
                "gpt-4.1-nano",              new double[]{0.10, 0.40},
                "gpt-4o-mini",               new double[]{0.15, 0.60},
                "gpt-4o",                    new double[]{2.50, 10.00},
                "text-embedding-3-large",    new double[]{0.13, 0.00},
                "text-embedding-3-small",    new double[]{0.02, 0.00}
        );

        /** 알 수 없는 모델에 적용할 기본 비용 (보수적 추정). */
        private static final double[] DEFAULT_COST = {0.50, 2.00};

        PipelineTrace(String inquiryId, Instant startTime) {
            this.inquiryId = inquiryId;
            this.startTime = startTime;
        }

        void addCall(LlmCallRecord record) {
            calls.add(record);
        }

        public String getInquiryId() {
            return inquiryId;
        }

        public Instant getStartTime() {
            return startTime;
        }

        public List<LlmCallRecord> getCalls() {
            return Collections.unmodifiableList(calls);
        }

        public int totalInputTokens() {
            return calls.stream().mapToInt(LlmCallRecord::inputTokens).sum();
        }

        public int totalOutputTokens() {
            return calls.stream().mapToInt(LlmCallRecord::outputTokens).sum();
        }

        public int totalTokens() {
            return totalInputTokens() + totalOutputTokens();
        }

        public int totalLlmCalls() {
            return calls.size();
        }

        /**
         * 모든 LLM 호출의 추정 비용(USD)을 합산한다.
         * 모델별 단가 테이블에 없는 모델은 보수적 기본값을 사용한다.
         */
        public double estimatedCostUsd() {
            double total = 0.0;
            for (LlmCallRecord call : calls) {
                double[] rates = resolveCost(call.model());
                total += (call.inputTokens() / 1_000_000.0) * rates[0]
                        + (call.outputTokens() / 1_000_000.0) * rates[1];
            }
            return total;
        }

        /**
         * 모든 LLM 호출의 레이턴시 합계(ms).
         */
        public long totalLatencyMs() {
            return calls.stream().mapToLong(LlmCallRecord::latencyMs).sum();
        }

        /**
         * 파이프라인 시작부터 현재까지의 벽시계 경과 시간.
         */
        public Duration wallClockDuration() {
            return Duration.between(startTime, Instant.now());
        }

        private static double[] resolveCost(String model) {
            if (model == null) {
                return DEFAULT_COST;
            }
            String lower = model.toLowerCase();
            // 정확히 일치하는 모델이 있으면 사용
            double[] exact = COST_PER_MILLION.get(lower);
            if (exact != null) {
                return exact;
            }
            // 접두사 기반 폴백: 모델 이름이 알려진 키로 시작하는 경우
            for (var entry : COST_PER_MILLION.entrySet()) {
                if (lower.startsWith(entry.getKey())) {
                    return entry.getValue();
                }
            }
            return DEFAULT_COST;
        }
    }
}
