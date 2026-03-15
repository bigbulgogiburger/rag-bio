package com.biorad.csrag.infrastructure.persistence.image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImageJpaRepository extends JpaRepository<ImageJpaEntity, UUID> {

    List<ImageJpaEntity> findByInquiryId(UUID inquiryId);
}
