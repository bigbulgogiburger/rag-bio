package com.biorad.csrag.infrastructure.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenAI API 응답에서 토큰 사용량을 추출하여 {@link PipelineTraceContext}에 기록.
 *
 * <p>Chat Completions 응답의 {@code usage} 필드를 파싱한다:
 * <pre>
 * {
 *   "usage": {
 *     "prompt_tokens": 1234,
 *     "completion_tokens": 567,
 *     "total_tokens": 1801
 *   }
 * }
 * </pre>
 *
 * <p>Embedding 응답도 동일한 형식의 {@code usage} 필드를 가지며,
 * {@code completion_tokens}가 없을 경우 0으로 처리한다.
 *
 * <p>파싱 실패 시 경고 로그만 출력하고 예외를 던지지 않는다 (non-breaking).
 */
public final class OpenAiResponseParser {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponseParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiResponseParser() {}

    /**
     * OpenAI API 원본 JSON 응답에서 토큰 사용량을 추출하여 현재 파이프라인 추적에 기록한다.
     *
     * @param rawResponse OpenAI API의 원본 JSON 응답 문자열
     * @param step        파이프라인 단계 이름 (e.g. "retrieve", "verify", "compose")
     * @param model       사용된 모델명 (e.g. "gpt-5-mini")
     * @param latencyMs   API 호출 소요 시간 (밀리초)
     */
    public static void recordUsage(String rawResponse, String step, String model, long latencyMs) {
        if (rawResponse == null || rawResponse.isBlank()) {
            log.warn("pipeline.trace.parse 빈 응답 — step={} model={}", step, model);
            return;
        }

        try {
            JsonNode root = MAPPER.readTree(rawResponse);
            JsonNode usage = root.path("usage");

            if (usage.isMissingNode() || usage.isNull()) {
                log.warn("pipeline.trace.parse usage 필드 없음 — step={} model={}", step, model);
                return;
            }

            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int completionTokens = usage.path("completion_tokens").asInt(0);

            PipelineTraceContext.recordLlmCall(step, model, promptTokens, completionTokens, latencyMs);

            if (log.isDebugEnabled()) {
                log.debug("pipeline.trace.parse step={} model={} prompt={} completion={} latency={}ms",
                        step, model, promptTokens, completionTokens, latencyMs);
            }
        } catch (Exception e) {
            log.warn("pipeline.trace.parse JSON 파싱 실패 — step={} model={} error={}",
                    step, model, e.getMessage());
        }
    }

    /**
     * 토큰 사용량을 직접 지정하여 현재 파이프라인 추적에 기록한다.
     * JSON 파싱이 불필요한 경우 (이미 파싱된 응답 또는 수동 기록) 사용.
     *
     * @param step          파이프라인 단계 이름
     * @param model         사용된 모델명
     * @param inputTokens   입력 토큰 수
     * @param outputTokens  출력 토큰 수
     * @param latencyMs     API 호출 소요 시간 (밀리초)
     */
    public static void recordUsageDirect(String step, String model,
                                         int inputTokens, int outputTokens,
                                         long latencyMs) {
        PipelineTraceContext.recordLlmCall(step, model, inputTokens, outputTokens, latencyMs);
    }
}
