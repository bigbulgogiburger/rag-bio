package com.biorad.csrag.infrastructure.persistence.answer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerDraftJpaRepository extends JpaRepository<AnswerDraftJpaEntity, UUID> {
    Optional<AnswerDraftJpaEntity> findTopByInquiryIdOrderByVersionDesc(UUID inquiryId);
    List<AnswerDraftJpaEntity> findByInquiryIdOrderByVersionDesc(UUID inquiryId);
    Optional<AnswerDraftJpaEntity> findByIdAndInquiryId(UUID id, UUID inquiryId);
}
