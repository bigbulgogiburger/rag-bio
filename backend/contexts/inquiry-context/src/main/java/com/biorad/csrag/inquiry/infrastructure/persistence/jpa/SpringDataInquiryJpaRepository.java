package com.biorad.csrag.inquiry.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpringDataInquiryJpaRepository extends JpaRepository<InquiryJpaEntity, UUID>,
                                                        JpaSpecificationExecutor<InquiryJpaEntity> {
}
