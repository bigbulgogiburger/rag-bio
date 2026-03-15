package com.biorad.csrag.infrastructure.openai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PipelineTraceContextTokenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeEach
    void setUp() {
        PipelineTraceContext.start("test-inquiry-1");
    }

    @AfterEach
    void tearDown() {
        PipelineTraceContext.finish(); // ThreadLocal 정리
    }

    // ── 토큰 기록 및 합산 ──────────────────────────────────────

    @Test
    void recordTokenUsage_singleStep_accumulatesCorrectly() {
        PipelineTraceContext.recordTokenUsage("retrieve", 500, 200, "gpt-5-mini");

        assertThat(PipelineTraceContext.getTotalPromptTokens()).isEqualTo(500);
        assertThat(PipelineTraceContext.getTotalCompletionTokens()).isEqualTo(200);
        assertThat(PipelineTraceContext.getTotalTokens()).isEqualTo(700);
    }

    @Test
    void recordTokenUsage_multipleSteps_sumsAll() {
        PipelineTraceContext.recordTokenUsage("retrieve", 500, 200, "gpt-5-mini");
        PipelineTraceContext.recordTokenUsage("verify", 800, 300, "gpt-5-mini");
        PipelineTraceContext.recordTokenUsage("compose", 1200, 600, "gpt-5-mini");

        assertThat(PipelineTraceContext.getTotalPromptTokens()).isEqualTo(2500);
        assertThat(PipelineTraceContext.getTotalCompletionTokens()).isEqualTo(1100);
        assertThat(PipelineTraceContext.getTotalTokens()).isEqualTo(3600);
    }

    @Test
    void recordLlmCall_andRecordTokenUsage_mixedCalls_sumsCorrectly() {
        PipelineTraceContext.recordLlmCall("retrieve", "gpt-5-mini", 500, 200, 150L);
        PipelineTraceContext.recordTokenUsage("verify", 800, 300, "gpt-5-nano");

        assertThat(PipelineTraceContext.getTotalPromptTokens()).isEqualTo(1300);
        assertThat(PipelineTraceContext.getTotalCompletionTokens()).isEqualTo(500);
        assertThat(PipelineTraceContext.getTotalTokens()).isEqualTo(1800);
    }

    @Test
    void noTraceStarted_returnsZeros() {
        PipelineTraceContext.finish(); // 기존 추적 종료

        assertThat(PipelineTraceContext.getTotalPromptTokens()).isEqualTo(0);
        assertThat(PipelineTraceContext.getTotalCompletionTokens()).isEqualTo(0);
        assertThat(PipelineTraceContext.getTotalTokens()).isEqualTo(0);
        assertThat(PipelineTraceContext.getEstimatedCostUsd()).isEqualTo(0.0);
        assertThat(PipelineTraceContext.getTokenUsageDetail()).isEqualTo("[]");
    }

    // ── 비용 추정 ──────────────────────────────────────────────

    @Test
    void estimatedCost_miniModel() {
        // gpt-5-mini: input $0.40/1M, output $1.60/1M (COST_PER_MILLION에 정확히 등록됨)
        PipelineTraceContext.recordTokenUsage("compose", 1_000_000, 1_000_000, "gpt-5-mini");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        // $0.40 + $1.60 = $2.00
        assertThat(cost).isCloseTo(2.00, within(0.001));
    }

    @Test
    void estimatedCost_nanoModel() {
        // gpt-5-nano: input $0.10/1M, output $0.40/1M (COST_PER_MILLION에 정확히 등록됨)
        PipelineTraceContext.recordTokenUsage("retrieve", 1_000_000, 1_000_000, "gpt-5-nano");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        // $0.10 + $0.40 = $0.50
        assertThat(cost).isCloseTo(0.50, within(0.001));
    }

    @Test
    void estimatedCost_embeddingModel() {
        // text-embedding-3-large: input $0.13/1M, output $0.00/1M
        PipelineTraceContext.recordTokenUsage("embed", 1_000_000, 0, "text-embedding-3-large");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        assertThat(cost).isCloseTo(0.13, within(0.001));
    }

    @Test
    void estimatedCost_unknownNanoModel_usesTierFallback() {
        // 알 수 없는 "nano" 포함 모델: 티어 폴백 $0.50/$2.00
        PipelineTraceContext.recordTokenUsage("step", 1_000_000, 1_000_000, "future-nano-v2");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        // $0.50 + $2.00 = $2.50
        assertThat(cost).isCloseTo(2.50, within(0.001));
    }

    @Test
    void estimatedCost_unknownMiniModel_usesTierFallback() {
        // 알 수 없는 "mini" 포함 모델: 티어 폴백 $3.00/$12.00
        PipelineTraceContext.recordTokenUsage("step", 1_000_000, 1_000_000, "future-mini-v2");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        // $3.00 + $12.00 = $15.00
        assertThat(cost).isCloseTo(15.00, within(0.001));
    }

    @Test
    void estimatedCost_unknownEmbeddingModel_usesTierFallback() {
        // 알 수 없는 "embedding" 포함 모델: 티어 폴백 $0.13/$0.00
        PipelineTraceContext.recordTokenUsage("embed", 1_000_000, 0, "custom-embedding-v1");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        assertThat(cost).isCloseTo(0.13, within(0.001));
    }

    @Test
    void estimatedCost_totallyUnknownModel_usesDefaultCost() {
        // 완전히 알 수 없는 모델: 기본값 $3.00/$12.00
        PipelineTraceContext.recordTokenUsage("step", 1_000_000, 1_000_000, "completely-unknown-model");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        // $3.00 + $12.00 = $15.00
        assertThat(cost).isCloseTo(15.00, within(0.001));
    }

    @Test
    void estimatedCost_multipleSteps_differentModels() {
        // gpt-5-mini: 1000 input ($0.0004), 500 output ($0.0008)
        PipelineTraceContext.recordTokenUsage("compose", 1000, 500, "gpt-5-mini");
        // gpt-5-nano: 2000 input ($0.0002), 300 output ($0.00012)
        PipelineTraceContext.recordTokenUsage("retrieve", 2000, 300, "gpt-5-nano");
        // text-embedding-3-large: 5000 input ($0.00065), 0 output
        PipelineTraceContext.recordTokenUsage("embed", 5000, 0, "text-embedding-3-large");

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        double expected = (1000.0 / 1_000_000 * 0.40) + (500.0 / 1_000_000 * 1.60)
                + (2000.0 / 1_000_000 * 0.10) + (300.0 / 1_000_000 * 0.40)
                + (5000.0 / 1_000_000 * 0.13);
        assertThat(cost).isCloseTo(expected, within(0.000001));
    }

    // ── JSON 직렬화 ────────────────────────────────────────────

    @Test
    void tokenUsageDetail_emptyPipeline_returnsEmptyArray() {
        String json = PipelineTraceContext.getTokenUsageDetail();
        assertThat(json).isEqualTo("[]");
    }

    @Test
    void tokenUsageDetail_singleStep_containsAllFields() throws Exception {
        PipelineTraceContext.recordLlmCall("compose", "gpt-5-mini", 1200, 600, 350L);

        String json = PipelineTraceContext.getTokenUsageDetail();
        List<Map<String, Object>> details = MAPPER.readValue(json, new TypeReference<>() {});

        assertThat(details).hasSize(1);
        Map<String, Object> entry = details.get(0);
        assertThat(entry.get("step")).isEqualTo("compose");
        assertThat(entry.get("model")).isEqualTo("gpt-5-mini");
        assertThat(entry.get("promptTokens")).isEqualTo(1200);
        assertThat(entry.get("completionTokens")).isEqualTo(600);
        assertThat(entry.get("totalTokens")).isEqualTo(1800);
        assertThat(entry.get("latencyMs")).isEqualTo(350);
    }

    @Test
    void tokenUsageDetail_multipleSteps_preservesOrder() throws Exception {
        PipelineTraceContext.recordLlmCall("retrieve", "gpt-5-nano", 500, 200, 100L);
        PipelineTraceContext.recordLlmCall("verify", "gpt-5-mini", 800, 300, 200L);
        PipelineTraceContext.recordLlmCall("compose", "gpt-5-mini", 1200, 600, 350L);

        String json = PipelineTraceContext.getTokenUsageDetail();
        List<Map<String, Object>> details = MAPPER.readValue(json, new TypeReference<>() {});

        assertThat(details).hasSize(3);
        assertThat(details.get(0).get("step")).isEqualTo("retrieve");
        assertThat(details.get(1).get("step")).isEqualTo("verify");
        assertThat(details.get(2).get("step")).isEqualTo("compose");
    }

    // ── StepTokenUsage 레코드 ──────────────────────────────────

    @Test
    void stepTokenUsages_reflectsRecordedCalls() {
        PipelineTraceContext.recordTokenUsage("retrieve", 500, 200, "gpt-5-nano");
        PipelineTraceContext.recordTokenUsage("compose", 1200, 600, "gpt-5-mini");

        PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.current();
        assertThat(trace).isNotNull();

        List<PipelineTraceContext.StepTokenUsage> usages = trace.getStepTokenUsages();
        assertThat(usages).hasSize(2);

        PipelineTraceContext.StepTokenUsage first = usages.get(0);
        assertThat(first.stepName()).isEqualTo("retrieve");
        assertThat(first.promptTokens()).isEqualTo(500);
        assertThat(first.completionTokens()).isEqualTo(200);
        assertThat(first.totalTokens()).isEqualTo(700);
        assertThat(first.modelId()).isEqualTo("gpt-5-nano");

        PipelineTraceContext.StepTokenUsage second = usages.get(1);
        assertThat(second.stepName()).isEqualTo("compose");
        assertThat(second.totalTokens()).isEqualTo(1800);
    }

    // ── finish() 후 정리 ───────────────────────────────────────

    @Test
    void finish_returnsTraceAndClearsThreadLocal() {
        PipelineTraceContext.recordTokenUsage("step1", 100, 50, "gpt-5-mini");

        PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.finish();
        assertThat(trace).isNotNull();
        assertThat(trace.totalTokens()).isEqualTo(150);

        // ThreadLocal이 정리되었으므로 이후 호출은 0을 반환해야 함
        assertThat(PipelineTraceContext.getTotalTokens()).isEqualTo(0);
        assertThat(PipelineTraceContext.current()).isNull();
    }

    // ── recordTokenUsage no-op when no trace ────────────────────

    @Test
    void recordTokenUsage_noTraceStarted_doesNotThrow() {
        PipelineTraceContext.finish(); // 기존 추적 종료

        // 예외 없이 무시되어야 함
        PipelineTraceContext.recordTokenUsage("step", 100, 50, "gpt-5-mini");
        assertThat(PipelineTraceContext.getTotalTokens()).isEqualTo(0);
    }

    // ── null 모델 처리 ─────────────────────────────────────────

    @Test
    void estimatedCost_nullModel_usesDefaultCost() {
        PipelineTraceContext.recordTokenUsage("step", 1_000_000, 1_000_000, null);

        double cost = PipelineTraceContext.getEstimatedCostUsd();
        // default: $3.00 + $12.00 = $15.00
        assertThat(cost).isCloseTo(15.00, within(0.001));
    }
}
