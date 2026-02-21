package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;

import java.util.List;

/**
 * 하위 질문별 증거 매핑 DTO.
 *
 * @param subQuestion 하위 질문
 * @param evidences   해당 질문에 대한 증거 목록
 * @param maxScore    증거 중 최고 점수
 * @param sufficient  증거가 답변하기에 충분한지 여부
 */
public record PerQuestionEvidence(
        SubQuestion subQuestion,
        List<EvidenceItem> evidences,
        double maxScore,
        boolean sufficient
) {
    private static final double MIN_RELEVANCE_THRESHOLD = 0.40;

    public static PerQuestionEvidence of(SubQuestion subQuestion, List<EvidenceItem> evidences) {
        double max = evidences.stream()
                .mapToDouble(EvidenceItem::score)
                .max()
                .orElse(0.0);
        boolean sufficient = !evidences.isEmpty() && max >= MIN_RELEVANCE_THRESHOLD;
        return new PerQuestionEvidence(subQuestion, evidences, max, sufficient);
    }
}
