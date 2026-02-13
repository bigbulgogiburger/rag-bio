package com.biorad.csrag.infrastructure.persistence.orchestration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrchestrationRunJpaRepository extends JpaRepository<OrchestrationRunJpaEntity, UUID> {
}
