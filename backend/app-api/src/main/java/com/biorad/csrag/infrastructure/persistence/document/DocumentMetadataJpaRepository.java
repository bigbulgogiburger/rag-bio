package com.biorad.csrag.infrastructure.persistence.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentMetadataJpaRepository extends JpaRepository<DocumentMetadataJpaEntity, UUID> {
}
