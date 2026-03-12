package com.biorad.csrag.infrastructure.openai;

import com.biorad.csrag.infrastructure.openai.PipelineTraceContext.LlmCallRecord;
import com.biorad.csrag.infrastructure.openai.PipelineTraceContext.PipelineTrace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 파이프라인 추적 결과를 구조화된 로그로 출력.
 *
 * <p>로그 형식은 ELK/Datadog 등 로그 수집기에서 파싱하기 쉬운
 * key=value 형식을 사용한다.
 *
 * <p>두 가지 레벨의 로그를 출력:
 * <ol>
 *   <li><b>Summary</b> — 파이프라인 전체 요약 (총 토큰, 비용, 레이턴시)</li>
 *   <li><b>Per-call</b> — 개별 LLM 호출 상세 (step, model, 토큰, 레이턴시)</li>
 * </ol>
 */
@Component
public class PipelineTraceLogger {

    private static final Logger log = LoggerFactory.getLogger(PipelineTraceLogger.class);

    /**
     * 파이프라인 추적 결과를 로그에 기록한다.
     *
     * @param trace 완료된 파이프라인 추적 데이터. {@code null}이면 경고 로그만 출력.
     */
    public void logTrace(PipelineTrace trace) {
        if (trace == null) {
            log.warn("pipeline.trace.summary 추적 데이터 없음 (trace=null)");
            return;
        }

        log.info("pipeline.trace.summary inquiryId={} totalCalls={} totalTokens={} "
                        + "inputTokens={} outputTokens={} estimatedCost=${} totalLatencyMs={}",
                trace.getInquiryId(),
                trace.totalLlmCalls(),
                trace.totalTokens(),
                trace.totalInputTokens(),
                trace.totalOutputTokens(),
                String.format("%.4f", trace.estimatedCostUsd()),
                trace.totalLatencyMs());

        for (LlmCallRecord call : trace.getCalls()) {
            log.info("pipeline.trace.call inquiryId={} step={} model={} "
                            + "input={} output={} latency={}ms",
                    trace.getInquiryId(),
                    call.step(),
                    call.model(),
                    call.inputTokens(),
                    call.outputTokens(),
                    call.latencyMs());
        }
    }
}
