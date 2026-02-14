package com.biorad.csrag.infrastructure.persistence.knowledge;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

/**
 * Knowledge Base 문서 Repository
 */
public interface KnowledgeDocumentJpaRepository
        extends JpaRepository<KnowledgeDocumentJpaEntity, UUID>,
                JpaSpecificationExecutor<KnowledgeDocumentJpaEntity> {

    /**
     * 특정 상태 목록에 해당하는 문서 조회
     */
    List<KnowledgeDocumentJpaEntity> findByStatusIn(List<String> statuses);

    /**
     * 특정 상태의 문서 수 카운트
     */
    int countByStatus(String status);
}
