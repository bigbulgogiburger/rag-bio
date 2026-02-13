package com.biorad.csrag.infrastructure.persistence.sendattempt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SendAttemptJpaRepository extends JpaRepository<SendAttemptJpaEntity, UUID> {
    long countByOutcome(String outcome);
}
