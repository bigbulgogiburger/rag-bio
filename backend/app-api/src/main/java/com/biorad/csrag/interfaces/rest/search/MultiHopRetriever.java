package com.biorad.csrag.interfaces.rest.search;

import java.util.List;
import java.util.UUID;

/**
 * 다중 문서 교차 추론을 위한 Multi-hop 검색기.
 * 복합 질문에서 여러 문서의 정보를 연결하여 정확한 답변을 찾음.
 */
public interface MultiHopRetriever {

    MultiHopResult retrieve(String question, UUID inquiryId);

    record MultiHopResult(
        List<RerankingService.RerankResult> evidences,
        List<HopRecord> hops,
        boolean isSingleHop
    ) {
        public static MultiHopResult singleHop(List<RerankingService.RerankResult> evidences) {
            return new MultiHopResult(evidences, List.of(), true);
        }
        public static MultiHopResult multiHop(List<RerankingService.RerankResult> evidences, List<HopRecord> hops) {
            return new MultiHopResult(evidences, hops, false);
        }
    }

    record HopRecord(int hopNumber, String query, int resultsCount, double topScore) {}
}
