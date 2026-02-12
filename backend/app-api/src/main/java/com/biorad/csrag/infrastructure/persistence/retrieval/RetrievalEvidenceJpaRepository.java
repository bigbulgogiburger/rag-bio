package com.biorad.csrag.infrastructure.persistence.retrieval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RetrievalEvidenceJpaRepository extends JpaRepository<RetrievalEvidenceJpaEntity, UUID> {
}
