package com.biorad.csrag.infrastructure.persistence.chunk;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentChunkJpaRepository extends JpaRepository<DocumentChunkJpaEntity, UUID> {

    void deleteByDocumentId(UUID documentId);

    List<DocumentChunkJpaEntity> findByDocumentIdOrderByChunkIndexAsc(UUID documentId);

    /**
     * 특정 source_type의 청크 수 카운트
     */
    long countBySourceType(String sourceType);
}
