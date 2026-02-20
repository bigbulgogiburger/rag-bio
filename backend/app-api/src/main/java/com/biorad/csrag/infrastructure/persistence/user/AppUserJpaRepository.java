package com.biorad.csrag.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserJpaRepository extends JpaRepository<AppUserJpaEntity, UUID> {
    Optional<AppUserJpaEntity> findByUsername(String username);
    boolean existsByUsername(String username);
}
