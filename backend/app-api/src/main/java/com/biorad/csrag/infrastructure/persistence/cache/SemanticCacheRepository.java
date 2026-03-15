package com.biorad.csrag.infrastructure.persistence.cache;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface SemanticCacheRepository extends JpaRepository<SemanticCacheEntity, Long> {

    Optional<SemanticCacheEntity> findByQueryEmbeddingHashAndInvalidatedFalse(String queryEmbeddingHash);

    @Modifying
    @Query("UPDATE SemanticCacheEntity c SET c.invalidated = true WHERE c.inquiryId = :inquiryId")
    void invalidateByInquiryId(@Param("inquiryId") Long inquiryId);

    @Modifying
    @Query("DELETE FROM SemanticCacheEntity c WHERE c.expiresAt < :now")
    void deleteExpired(@Param("now") Instant now);

    @Modifying
    @Query("UPDATE SemanticCacheEntity c SET c.hitCount = c.hitCount + 1 WHERE c.id = :id")
    void incrementHitCount(@Param("id") Long id);
}
