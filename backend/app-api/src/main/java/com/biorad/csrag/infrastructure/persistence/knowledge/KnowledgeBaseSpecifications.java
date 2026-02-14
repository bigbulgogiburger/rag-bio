package com.biorad.csrag.infrastructure.persistence.knowledge;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Knowledge Base 문서 동적 쿼리를 위한 Specification 빌더
 */
public class KnowledgeBaseSpecifications {

    /**
     * 필터 조건을 조합하여 Specification을 생성
     *
     * @param category      카테고리 필터
     * @param productFamily 제품군 필터
     * @param status        상태 필터
     * @param keyword       제목/설명 키워드 검색
     * @return 조합된 Specification
     */
    public static Specification<KnowledgeDocumentJpaEntity> withFilters(
            String category,
            String productFamily,
            String status,
            String keyword
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 카테고리 필터
            if (category != null && !category.isBlank()) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            // 제품군 필터
            if (productFamily != null && !productFamily.isBlank()) {
                predicates.add(cb.equal(root.get("productFamily"), productFamily));
            }

            // 상태 필터
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // 키워드 검색 (제목 또는 설명에서 대소문자 무시 LIKE)
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), pattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
