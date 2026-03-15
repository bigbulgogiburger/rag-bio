package com.biorad.csrag.infrastructure.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

    private static final Logger log = LoggerFactory.getLogger(PipelineTraceContext.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
     * 현재 파이프라인 추적에 토큰 사용량을 기록한다.
     * {@link #recordLlmCall}의 간편 버전으로, 레이턴시 없이 토큰만 기록할 때 사용.
     * 추적이 시작되지 않은 상태에서 호출하면 아무 동작도 하지 않는다 (no-op).
     */
    public static void recordTokenUsage(String stepName, int promptTokens, int completionTokens, String modelId) {
        recordLlmCall(stepName, modelId, promptTokens, completionTokens, 0L);
    }

    /**
     * 현재 파이프라인의 전체 프롬프트 토큰 합계를 반환한다.
     * 추적이 시작되지 않은 경우 0을 반환한다.
     */
    public static int getTotalPromptTokens() {
        PipelineTrace trace = CURRENT.get();
        return trace != null ? trace.totalInputTokens() : 0;
    }

    /**
     * 현재 파이프라인의 전체 완료 토큰 합계를 반환한다.
     * 추적이 시작되지 않은 경우 0을 반환한다.
     */
    public static int getTotalCompletionTokens() {
        PipelineTrace trace = CURRENT.get();
        return trace != null ? trace.totalOutputTokens() : 0;
    }

    /**
     * 현재 파이프라인의 전체 토큰 합계를 반환한다.
     * 추적이 시작되지 않은 경우 0을 반환한다.
     */
    public static int getTotalTokens() {
        PipelineTrace trace = CURRENT.get();
        return trace != null ? trace.totalTokens() : 0;
    }

    /**
     * 현재 파이프라인의 추정 비용(USD)을 반환한다.
     * 추적이 시작되지 않은 경우 0.0을 반환한다.
     */
    public static double getEstimatedCostUsd() {
        PipelineTrace trace = CURRENT.get();
        return trace != null ? trace.estimatedCostUsd() : 0.0;
    }

    /**
     * 현재 파이프라인의 단계별 토큰 사용량을 JSON 문자열로 반환한다.
     * 추적이 시작되지 않은 경우 빈 배열 {@code "[]"}을 반환한다.
     */
    public static String getTokenUsageDetail() {
        PipelineTrace trace = CURRENT.get();
        return trace != null ? trace.tokenUsageDetailJson() : "[]";
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
     * 단계별 토큰 사용량 요약.
     */
    public record StepTokenUsage(String stepName, int promptTokens, int completionTokens, int totalTokens, String modelId) {}

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

        /** 알 수 없는 모델에 적용할 기본 비용 (보수적 추정, mini 티어 기준). */
        private static final double[] DEFAULT_COST = {3.00, 12.00};

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

        /**
         * 단계별 토큰 사용량 요약 리스트를 반환한다.
         */
        public List<StepTokenUsage> getStepTokenUsages() {
            return calls.stream()
                    .map(c -> new StepTokenUsage(
                            c.step(), c.inputTokens(), c.outputTokens(), c.totalTokens(), c.model()))
                    .toList();
        }

        /**
         * 단계별 토큰 사용량을 JSON 문자열로 직렬화한다.
         * 직렬화 실패 시 빈 배열 {@code "[]"}을 반환한다.
         */
        public String tokenUsageDetailJson() {
            try {
                List<Map<String, Object>> details = new ArrayList<>();
                for (LlmCallRecord call : calls) {
                    var entry = new LinkedHashMap<String, Object>();
                    entry.put("step", call.step());
                    entry.put("model", call.model());
                    entry.put("promptTokens", call.inputTokens());
                    entry.put("completionTokens", call.outputTokens());
                    entry.put("totalTokens", call.totalTokens());
                    entry.put("latencyMs", call.latencyMs());
                    details.add(entry);
                }
                return MAPPER.writeValueAsString(details);
            } catch (JsonProcessingException e) {
                log.warn("pipeline.trace 토큰 사용량 JSON 직렬화 실패: {}", e.getMessage());
                return "[]";
            }
        }

        /** 티어 기반 폴백 비용 (per 1M tokens): [input, output] */
        private static final double[] TIER_NANO = {0.50, 2.00};
        private static final double[] TIER_MINI = {3.00, 12.00};
        private static final double[] TIER_EMBEDDING = {0.13, 0.00};

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
            // 티어 기반 폴백: 모델 이름에 포함된 키워드로 비용 추정
            if (lower.contains("embedding")) {
                return TIER_EMBEDDING;
            }
            if (lower.contains("nano")) {
                return TIER_NANO;
            }
            if (lower.contains("mini")) {
                return TIER_MINI;
            }
            return DEFAULT_COST;
        }
    }
}
