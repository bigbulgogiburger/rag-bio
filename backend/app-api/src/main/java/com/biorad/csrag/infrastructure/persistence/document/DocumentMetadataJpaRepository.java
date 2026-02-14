package com.biorad.csrag.infrastructure.persistence.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentMetadataJpaRepository extends JpaRepository<DocumentMetadataJpaEntity, UUID> {

    List<DocumentMetadataJpaEntity> findByInquiryIdOrderByCreatedAtDesc(UUID inquiryId);

    /**
     * 특정 문의에 첨부된 문서 수를 카운트
     */
    int countByInquiryId(UUID inquiryId);
}
