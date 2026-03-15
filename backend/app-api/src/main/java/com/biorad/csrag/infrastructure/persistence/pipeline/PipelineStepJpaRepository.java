package com.biorad.csrag.infrastructure.persistence.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineStepJpaRepository extends JpaRepository<PipelineStepJpaEntity, UUID> {

    List<PipelineStepJpaEntity> findByExecutionIdOrderByUpdatedAtAsc(UUID executionId);

    Optional<PipelineStepJpaEntity> findByExecutionIdAndStepName(UUID executionId, String stepName);
}
