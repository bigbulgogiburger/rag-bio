package com.biorad.csrag.interfaces.rest.search;

import java.util.List;
import java.util.UUID;

/**
 * OpenAI Function Calling 기반 Tool-Calling 검색 에이전트.
 * LLM이 질문을 분석하고 적절한 검색 도구를 선택하여 실행.
 */
public interface SearchToolAgent {
    List<RerankingService.RerankResult> agenticSearch(String question, UUID inquiryId);
}
