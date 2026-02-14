package com.biorad.csrag.inquiry.infrastructure.persistence.jpa;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 문의 목록 동적 쿼리를 위한 Specification 빌더
 */
public class InquirySpecifications {

    /**
     * 필터 조건을 조합하여 Specification을 생성
     *
     * @param statuses  상태 필터 (복수 선택 가능)
     * @param channel   채널 필터
     * @param keyword   질문 내용 키워드 (LIKE 검색)
     * @param from      시작 일시
     * @param to        종료 일시
     * @return 조합된 Specification
     */
    public static Specification<InquiryJpaEntity> withFilters(
            List<String> statuses,
            String channel,
            String keyword,
            Instant from,
            Instant to
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 상태 필터 (IN 조건)
            if (statuses != null && !statuses.isEmpty()) {
                predicates.add(root.get("status").as(String.class).in(statuses));
            }

            // 채널 필터 (완전 일치)
            if (channel != null && !channel.isBlank()) {
                predicates.add(cb.equal(root.get("customerChannel"), channel));
            }

            // 키워드 검색 (대소문자 무시 LIKE)
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(cb.like(
                    cb.lower(root.get("question")),
                    "%" + keyword.toLowerCase() + "%"
                ));
            }

            // 시작 일시 필터 (이후)
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }

            // 종료 일시 필터 (이전)
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
