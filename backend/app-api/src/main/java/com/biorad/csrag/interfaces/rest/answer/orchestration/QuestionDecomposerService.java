package com.biorad.csrag.interfaces.rest.answer.orchestration;

/**
 * 고객 질문을 하위 질문으로 분해하는 서비스 인터페이스.
 * <p>
 * 구현체:
 * <ul>
 *   <li>{@code RegexQuestionDecomposerService} — 정규식 기반 (openai.enabled=false, 기본값)</li>
 *   <li>{@code OpenAiQuestionDecomposerService} — LLM 기반 (openai.enabled=true)</li>
 * </ul>
 */
public interface QuestionDecomposerService {

    /**
     * 질문을 분석하여 하위 질문으로 분해한다.
     *
     * @param question 원본 고객 질문
     * @return 분해 결과 (원본 + 하위 질문 리스트 + 제품 컨텍스트)
     */
    DecomposedQuestion decompose(String question);
}
