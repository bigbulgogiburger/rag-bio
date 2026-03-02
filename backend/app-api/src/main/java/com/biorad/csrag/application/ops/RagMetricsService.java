package com.biorad.csrag.application.ops;

import com.biorad.csrag.infrastructure.persistence.metrics.RagPipelineMetricEntity;
import com.biorad.csrag.infrastructure.persistence.metrics.RagPipelineMetricRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class RagMetricsService {

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
}
