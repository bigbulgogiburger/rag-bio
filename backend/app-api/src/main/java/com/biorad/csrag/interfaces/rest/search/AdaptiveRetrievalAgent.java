package com.biorad.csrag.interfaces.rest.search;

import java.util.List;
import java.util.UUID;

public interface AdaptiveRetrievalAgent {
    AdaptiveResult retrieve(String question, String productContext, UUID inquiryId);

    record AdaptiveResult(
            List<RerankingService.RerankResult> evidences,
            int attempts,
            double confidence,
            ResultStatus status
    ) {
        public enum ResultStatus { SUCCESS, LOW_CONFIDENCE, NO_EVIDENCE }

        public static AdaptiveResult success(List<RerankingService.RerankResult> evidences, int attempts) {
            double score = evidences.isEmpty() ? 0.0
                    : evidences.stream().mapToDouble(RerankingService.RerankResult::rerankScore).max().orElse(0.0);
            return new AdaptiveResult(evidences, attempts, score, ResultStatus.SUCCESS);
        }

        public static AdaptiveResult lowConfidence(List<RerankingService.RerankResult> evidences, double score) {
            return new AdaptiveResult(evidences, 3, score, ResultStatus.LOW_CONFIDENCE);
        }

        public static AdaptiveResult noEvidence(String question) {
            return new AdaptiveResult(List.of(), 3, 0.0, ResultStatus.NO_EVIDENCE);
        }
    }
}
