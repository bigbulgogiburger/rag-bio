package com.biorad.csrag.infrastructure.persistence.answer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AiReviewResultJpaRepository extends JpaRepository<AiReviewResultJpaEntity, UUID> {
    List<AiReviewResultJpaEntity> findByAnswerIdOrderByCreatedAtDesc(UUID answerId);
    List<AiReviewResultJpaEntity> findByInquiryIdOrderByCreatedAtDesc(UUID inquiryId);
}
