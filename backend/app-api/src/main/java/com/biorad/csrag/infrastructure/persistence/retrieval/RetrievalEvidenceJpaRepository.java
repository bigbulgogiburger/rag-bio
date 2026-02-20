package com.biorad.csrag.infrastructure.persistence.retrieval;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface RetrievalEvidenceJpaRepository extends JpaRepository<RetrievalEvidenceJpaEntity, UUID> {

    List<RetrievalEvidenceJpaEntity> findByCreatedAtBetween(Instant from, Instant to);

    @Query("""
            SELECT re.chunkId, COUNT(re) FROM RetrievalEvidenceJpaEntity re
            WHERE re.createdAt BETWEEN :from AND :to
            GROUP BY re.chunkId
            ORDER BY COUNT(re) DESC
            """)
    List<Object[]> findTopReferencedChunks(@Param("from") Instant from, @Param("to") Instant to);
}
