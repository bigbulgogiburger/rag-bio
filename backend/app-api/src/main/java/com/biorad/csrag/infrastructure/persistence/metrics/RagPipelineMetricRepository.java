package com.biorad.csrag.infrastructure.persistence.metrics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RagPipelineMetricRepository extends JpaRepository<RagPipelineMetricEntity, Long> {

    @Query("SELECT AVG(m.metricValue) FROM RagPipelineMetricEntity m WHERE m.metricType = :type")
    Optional<Double> findAvgByMetricType(@Param("type") String type);

    @Query("SELECT COUNT(m) FROM RagPipelineMetricEntity m WHERE m.metricType = :type AND m.metricValue > 0")
    long countPositiveByMetricType(@Param("type") String type);

    @Query("SELECT COUNT(m) FROM RagPipelineMetricEntity m WHERE m.metricType = :type")
    long countByMetricType(@Param("type") String type);
}
