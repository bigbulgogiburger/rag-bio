package com.biorad.csrag.interfaces.rest.answer.orchestration;

/**
 * 분해된 하위 질문 DTO.
 *
 * @param index    질문 번호 (1-based)
 * @param question 추출된 하위 질문 텍스트
 * @param context  원본 질문에서 파악된 맥락 (제품명 등, nullable)
 */
public record SubQuestion(
        int index,
        String question,
        String context
) {}
