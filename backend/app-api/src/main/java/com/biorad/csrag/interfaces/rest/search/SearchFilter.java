package com.biorad.csrag.interfaces.rest.search;

import java.util.Set;
import java.util.UUID;

/**
 * 검색 필터 DTO. 벡터/키워드 검색에 전달하여 결과 범위를 좁힌다.
 *
 * @param inquiryId        해당 문의 ID (해당 문의 문서 우선, nullable)
 * @param documentIds      특정 문서 ID 집합 (nullable = 전체 검색)
 * @param productFamilies  제품 패밀리 필터 (예: {"naica", "QX200"}; nullable = 전체)
 * @param sourceTypes      소스 타입 필터 (예: "INQUIRY", "KNOWLEDGE_BASE"; nullable = 전체)
 */
public record SearchFilter(
        UUID inquiryId,
        Set<UUID> documentIds,
        Set<String> productFamilies,
        Set<String> sourceTypes
) {
    /** 필터 없음 (전체 검색) */
    public static SearchFilter none() {
        return new SearchFilter(null, null, null, null);
    }

    /** 문의 스코핑만 적용 */
    public static SearchFilter forInquiry(UUID inquiryId) {
        return new SearchFilter(inquiryId, null, null, null);
    }

    /** 단일 제품 + 문의 스코핑 (하위 호환) */
    public static SearchFilter forProduct(UUID inquiryId, String productFamily) {
        return new SearchFilter(inquiryId, null, Set.of(productFamily), null);
    }

    /** 복수 제품 + 문의 스코핑 */
    public static SearchFilter forProducts(UUID inquiryId, Set<String> productFamilies) {
        return new SearchFilter(inquiryId, null, productFamilies, null);
    }

    /** 특정 문서 ID 집합으로 필터 */
    public static SearchFilter forDocuments(Set<UUID> documentIds) {
        return new SearchFilter(null, documentIds, null, null);
    }

    public boolean hasDocumentFilter() {
        return documentIds != null && !documentIds.isEmpty();
    }

    public boolean hasProductFilter() {
        return productFamilies != null && !productFamilies.isEmpty();
    }

    public boolean hasSourceTypeFilter() {
        return sourceTypes != null && !sourceTypes.isEmpty();
    }

    public boolean isEmpty() {
        return !hasDocumentFilter() && !hasProductFilter() && !hasSourceTypeFilter()
                && inquiryId == null;
    }
}
