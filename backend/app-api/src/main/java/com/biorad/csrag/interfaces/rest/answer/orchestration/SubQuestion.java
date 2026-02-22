package com.biorad.csrag.interfaces.rest.answer.orchestration;

import java.util.Set;

/**
 * 분해된 하위 질문 DTO.
 *
 * @param index            질문 번호 (1-based)
 * @param question         추출된 하위 질문 텍스트
 * @param context          원본 질문에서 파악된 맥락 (제품명 등, nullable)
 * @param productFamilies  하위 질문에서 추출된 제품 패밀리 Set (비어있을 수 있음)
 */
public record SubQuestion(
        int index,
        String question,
        String context,
        Set<String> productFamilies
) {
    /** 하위 호환: productFamilies 없이 3-arg 생성자 */
    public SubQuestion(int index, String question, String context) {
        this(index, question, context, Set.of());
    }
}
