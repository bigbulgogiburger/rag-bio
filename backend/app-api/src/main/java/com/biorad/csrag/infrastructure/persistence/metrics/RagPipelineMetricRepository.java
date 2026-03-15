package com.biorad.csrag.infrastructure.persistence.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RagPipelineMetricRepository extends JpaRepository<RagPipelineMetricEntity, Long> {

    @Query("SELECT AVG(m.metricValue) FROM RagPipelineMetricEntity m WHERE m.metricType = :type")
    Optional<Double> findAvgByMetricType(@Param("type") String type);

    @Query("SELECT COUNT(m) FROM RagPipelineMetricEntity m WHERE m.metricType = :type AND m.metricValue > 0")
    long countPositiveByMetricType(@Param("type") String type);

    @Query("SELECT COUNT(m) FROM RagPipelineMetricEntity m WHERE m.metricType = :type")
    long countByMetricType(@Param("type") String type);

    /**
     * 지정된 기간 내의 메트릭 목록을 조회한다.
     */
    List<RagPipelineMetricEntity> findByCreatedAtBetween(Instant start, Instant end);

    /**
     * 지정된 기간 내의 추정 비용(USD) 합계를 반환한다.
     * {@code estimated_cost_usd}가 null인 행은 제외된다.
     */
    @Query("SELECT COALESCE(SUM(m.estimatedCostUsd), 0.0) FROM RagPipelineMetricEntity m " +
           "WHERE m.createdAt BETWEEN :start AND :end AND m.estimatedCostUsd IS NOT NULL")
    double sumEstimatedCostBetween(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * 지정된 기간 내의 총 토큰 사용량을 반환한다.
     */
    @Query("SELECT COALESCE(SUM(m.totalTokens), 0) FROM RagPipelineMetricEntity m " +
           "WHERE m.createdAt BETWEEN :start AND :end AND m.totalTokens IS NOT NULL")
    long sumTotalTokensBetween(@Param("start") Instant start, @Param("end") Instant end);
}
