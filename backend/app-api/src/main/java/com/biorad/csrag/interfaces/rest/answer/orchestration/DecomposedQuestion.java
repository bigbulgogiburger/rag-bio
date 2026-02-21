package com.biorad.csrag.interfaces.rest.answer.orchestration;

import java.util.List;

/**
 * 질문 분해 결과 DTO.
 *
 * @param originalQuestion 원본 전체 질문
 * @param subQuestions     분리된 하위 질문들 (1개 이상)
 * @param productContext   질문에서 추출된 제품 맥락 (nullable)
 */
public record DecomposedQuestion(
        String originalQuestion,
        List<SubQuestion> subQuestions,
        String productContext
) {}
