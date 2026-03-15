package com.biorad.csrag.application.ops;

import com.biorad.csrag.infrastructure.openai.PipelineTraceContext;
import com.biorad.csrag.infrastructure.persistence.metrics.RagPipelineMetricEntity;
import com.biorad.csrag.infrastructure.persistence.metrics.RagPipelineMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

@Service
public class RagMetricsService {

    private static final Logger log = LoggerFactory.getLogger(RagMetricsService.class);

    private final RagPipelineMetricRepository repository;

    public RagMetricsService(RagPipelineMetricRepository repository) {
        this.repository = repository;
    }

    @Async
    public void record(Long inquiryId, String metricType, double value) {
        record(inquiryId, metricType, value, null);
    }

    @Async
    public void record(Long inquiryId, String metricType, double value, String details) {
        try {
            repository.save(new RagPipelineMetricEntity(inquiryId, metricType, value, details));
        } catch (Exception ignored) {
            // fire-and-forget: metric recording must never break the main pipeline
        }
    }

    /**
     * 파이프라인 메트릭을 저장하면서 현재 {@link PipelineTraceContext}의 토큰 사용량을 포함한다.
     * 토큰 추적이 시작되지 않은 경우에도 기본 메트릭은 저장된다.
     */
    @Async
    public void recordWithTokenTracking(Long inquiryId, String metricType, double value, String details) {
        try {
            var entity = new RagPipelineMetricEntity(inquiryId, metricType, value, details);

            PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.current();
            if (trace != null) {
                entity.setTotalPromptTokens(trace.totalInputTokens());
                entity.setTotalCompletionTokens(trace.totalOutputTokens());
                entity.setTotalTokens(trace.totalTokens());
                entity.setEstimatedCostUsd(trace.estimatedCostUsd());
                entity.setTokenUsageDetail(trace.tokenUsageDetailJson());

                if (log.isDebugEnabled()) {
                    log.debug("metrics.token inquiry={} tokens={} cost=${}", inquiryId,
                            trace.totalTokens(), String.format(Locale.US, "%.6f", trace.estimatedCostUsd()));
                }
            }

            repository.save(entity);
        } catch (Exception e) {
            // fire-and-forget: metric recording must never break the main pipeline
            log.warn("metrics.record 저장 실패: inquiryId={} type={} error={}", inquiryId, metricType, e.getMessage());
        }
    }

    /**
     * 오늘 하루의 추정 비용(USD) 합계와 총 토큰 사용량을 반환한다.
     */
    public DailyCostSummary getDailyCostSummary() {
        return getDailyCostSummary(LocalDate.now(ZoneId.of("Asia/Seoul")));
    }

    /**
     * 지정된 날짜의 추정 비용(USD) 합계와 총 토큰 사용량을 반환한다.
     */
    public DailyCostSummary getDailyCostSummary(LocalDate date) {
        ZoneId zone = ZoneId.of("Asia/Seoul");
        Instant start = date.atStartOfDay(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();

        double totalCost = repository.sumEstimatedCostBetween(start, end);
        long totalTokens = repository.sumTotalTokensBetween(start, end);

        return new DailyCostSummary(date, round6(totalCost), totalTokens);
    }

    public RagMetricsSummary getAggregatedMetrics() {
        double avgSearchScore = avg("SEARCH_SCORE");
        double avgRerankImprovement = avg("RERANK_IMPROVEMENT");
        double adaptiveRetryRate = rate("ADAPTIVE_RETRY");
        double hydeUsageRate = rate("HYDE_USAGE");
        double criticRevisionRate = rate("CRITIC_REVISION");
        double multiHopActivationRate = rate("MULTIHOP_ACTIVATION");
        double avgAnswerGenerationTimeMs = avg("ANSWER_GENERATION_TIME");
        double avgIndexingTimeMs = avg("INDEXING_TIME");

        return new RagMetricsSummary(
                round2(avgSearchScore),
                round2(avgRerankImprovement),
                round2(adaptiveRetryRate),
                round2(hydeUsageRate),
                round2(criticRevisionRate),
                round2(multiHopActivationRate),
                round2(avgAnswerGenerationTimeMs),
                round2(avgIndexingTimeMs)
        );
    }

    private double avg(String metricType) {
        return repository.findAvgByMetricType(metricType).orElse(0.0);
    }

    private double rate(String metricType) {
        long total = repository.countByMetricType(metricType);
        if (total == 0) return 0.0;
        long positive = repository.countPositiveByMetricType(metricType);
        return (positive * 100.0) / total;
    }

    private double round2(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.2f", value));
    }

    private double round6(double value) {
        return Double.parseDouble(String.format(Locale.US, "%.6f", value));
    }

    public record RagMetricsSummary(
            double avgSearchScore,
            double avgRerankImprovement,
            double adaptiveRetryRate,
            double hydeUsageRate,
            double criticRevisionRate,
            double multiHopActivationRate,
            double avgAnswerGenerationTimeMs,
            double avgIndexingTimeMs
    ) {}

    public record DailyCostSummary(
            LocalDate date,
            double totalEstimatedCostUsd,
            long totalTokens
    ) {}
}
