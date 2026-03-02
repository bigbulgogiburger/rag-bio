package com.biorad.csrag.evaluation;

import java.util.List;

/**
 * RAGAS 평가 결과 DTO.
 * 4개 메트릭(Faithfulness, Answer Relevancy, Context Precision, Context Recall)의
 * 평균값과 케이스별 상세 결과를 담는다.
 */
public record EvaluationReport(
        double faithfulness,
        double answerRelevancy,
        double contextPrecision,
        double contextRecall,
        List<CaseResult> caseResults
) {

    /**
     * 단일 골든 케이스에 대한 평가 결과.
     */
    public record CaseResult(
            String caseId,
            double faithfulness,
            double answerRelevancy,
            double contextPrecision,
            double contextRecall
    ) {

        /**
         * 케이스 결과 요약 문자열 반환.
         */
        @Override
        public String toString() {
            return String.format(
                    "CaseResult{id='%s', faithfulness=%.3f, answerRelevancy=%.3f, contextPrecision=%.3f, contextRecall=%.3f}",
                    caseId, faithfulness, answerRelevancy, contextPrecision, contextRecall
            );
        }
    }

    /**
     * 전체 평균 점수(4개 메트릭 산술 평균) 반환.
     */
    public double overallScore() {
        return (faithfulness + answerRelevancy + contextPrecision + contextRecall) / 4.0;
    }

    @Override
    public String toString() {
        return String.format(
                "EvaluationReport{faithfulness=%.3f, answerRelevancy=%.3f, contextPrecision=%.3f, contextRecall=%.3f, overall=%.3f, cases=%d}",
                faithfulness, answerRelevancy, contextPrecision, contextRecall, overallScore(), caseResults.size()
        );
    }
}
