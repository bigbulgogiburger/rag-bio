package com.biorad.csrag.interfaces.rest.search;

import java.util.List;

/**
 * HyDE (Hypothetical Document Embeddings) 변환기.
 * 사용자 질문을 가상의 이상적 답변으로 변환 후 임베딩하여
 * 문서와의 의미적 유사도를 높인다.
 */
public interface HydeQueryTransformer {

    /**
     * 사용자 질문을 HyDE 가상 답변으로 변환 후 임베딩 벡터를 반환한다.
     *
     * @param question       사용자 질문
     * @param productContext 제품 컨텍스트 (빈 문자열 허용)
     * @return 가상 답변의 임베딩 벡터
     */
    List<Double> transformAndEmbed(String question, String productContext);

    /** HyDE 활성화 여부 */
    default boolean isEnabled() {
        return true;
    }
}
