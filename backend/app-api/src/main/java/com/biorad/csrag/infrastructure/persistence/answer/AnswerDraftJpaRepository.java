package com.biorad.csrag.infrastructure.persistence.answer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerDraftJpaRepository extends JpaRepository<AnswerDraftJpaEntity, UUID> {
    Optional<AnswerDraftJpaEntity> findTopByInquiryIdOrderByVersionDesc(UUID inquiryId);
    List<AnswerDraftJpaEntity> findByInquiryIdOrderByVersionDesc(UUID inquiryId);
    Optional<AnswerDraftJpaEntity> findByIdAndInquiryId(UUID id, UUID inquiryId);

    @Query("""
            SELECT a FROM AnswerDraftJpaEntity a
            WHERE a.inquiryId = :inquiryId
              AND (:status IS NULL OR a.status = :status)
              AND (:fromTs IS NULL OR a.createdAt >= :fromTs)
              AND (:toTs IS NULL OR a.createdAt <= :toTs)
              AND (
                    :actor IS NULL
                    OR a.reviewedBy = :actor
                    OR a.approvedBy = :actor
                    OR a.sentBy = :actor
              )
            """)
    Page<AnswerDraftJpaEntity> searchAuditLogs(
            @Param("inquiryId") UUID inquiryId,
            @Param("status") String status,
            @Param("actor") String actor,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs,
            Pageable pageable
    );
}
