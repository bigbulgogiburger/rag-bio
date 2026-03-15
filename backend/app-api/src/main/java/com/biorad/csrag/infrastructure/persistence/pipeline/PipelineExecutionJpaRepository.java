package com.biorad.csrag.infrastructure.persistence.pipeline;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineExecutionJpaRepository extends JpaRepository<PipelineExecutionJpaEntity, UUID> {

    Optional<PipelineExecutionJpaEntity> findTopByInquiryIdOrderByCreatedAtDesc(UUID inquiryId);

    List<PipelineExecutionJpaEntity> findByInquiryIdAndStatus(UUID inquiryId, String status);
}
