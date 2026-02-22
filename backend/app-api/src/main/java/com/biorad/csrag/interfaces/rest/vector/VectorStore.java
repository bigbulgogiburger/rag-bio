package com.biorad.csrag.interfaces.rest.vector;

import com.biorad.csrag.interfaces.rest.search.SearchFilter;

import java.util.List;
import java.util.UUID;

public interface VectorStore {

    /**
     * 벡터 저장 (기존 방식 - 하위 호환)
     */
    void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content);

    /**
     * 벡터 저장 (sourceType 포함)
     *
     * @param chunkId    청크 ID
     * @param documentId 문서 ID
     * @param vector     임베딩 벡터
     * @param content    청크 내용
     * @param sourceType "INQUIRY" 또는 "KNOWLEDGE_BASE"
     */
    void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType);

    /**
     * 벡터 저장 (sourceType + productFamily 포함)
     *
     * @param chunkId       청크 ID
     * @param documentId    문서 ID
     * @param vector        임베딩 벡터
     * @param content       청크 내용
     * @param sourceType    "INQUIRY" 또는 "KNOWLEDGE_BASE"
     * @param productFamily 제품 패밀리 (예: "naica", "vericheck", nullable)
     */
    default void upsert(UUID chunkId, UUID documentId, List<Double> vector, String content, String sourceType, String productFamily) {
        upsert(chunkId, documentId, vector, content, sourceType);
    }

    /**
     * 벡터 검색 (필터 없음 - 하위 호환)
     */
    List<VectorSearchResult> search(List<Double> queryVector, int topK);

    /**
     * 벡터 검색 (필터 적용)
     *
     * @param queryVector 쿼리 벡터
     * @param topK        상위 K개 결과
     * @param filter      검색 필터 (문서 ID, 제품 패밀리 등)
     */
    default List<VectorSearchResult> search(List<Double> queryVector, int topK, SearchFilter filter) {
        return search(queryVector, topK);
    }

    /**
     * 특정 문서의 모든 벡터 삭제
     *
     * @param documentId 삭제할 문서 ID
     */
    void deleteByDocumentId(UUID documentId);
}
