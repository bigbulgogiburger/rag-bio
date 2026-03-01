package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerQuestionEvidenceTest {

    private static final SubQuestion SQ = new SubQuestion(1, "test question", null);

    @Test
    void of_emptyEvidences_notSufficientZeroScore() {
        PerQuestionEvidence result = PerQuestionEvidence.of(SQ, List.of());

        assertThat(result.sufficient()).isFalse();
        assertThat(result.maxScore()).isEqualTo(0.0);
        assertThat(result.evidences()).isEmpty();
    }

    @Test
    void of_highScoreEvidence_sufficient() {
        EvidenceItem highScore = new EvidenceItem("c1", "d1", 0.85, "content", "INQUIRY", "f.pdf", 1, 1);
        PerQuestionEvidence result = PerQuestionEvidence.of(SQ, List.of(highScore));

        assertThat(result.sufficient()).isTrue();
        assertThat(result.maxScore()).isEqualTo(0.85);
    }

    @Test
    void of_lowScoreEvidence_notSufficient() {
        EvidenceItem lowScore = new EvidenceItem("c2", "d2", 0.30, "content", "INQUIRY", null, null, null);
        PerQuestionEvidence result = PerQuestionEvidence.of(SQ, List.of(lowScore));

        // score 0.30 < MIN_RELEVANCE_THRESHOLD (0.40) → not sufficient
        assertThat(result.sufficient()).isFalse();
        assertThat(result.maxScore()).isEqualTo(0.30);
    }

    @Test
    void of_exactThresholdScore_sufficient() {
        // Score == 0.40 (boundary) → should be sufficient
        EvidenceItem threshold = new EvidenceItem("c3", "d3", 0.40, "content", "INQUIRY", null, null, null);
        PerQuestionEvidence result = PerQuestionEvidence.of(SQ, List.of(threshold));

        assertThat(result.sufficient()).isTrue();
        assertThat(result.maxScore()).isEqualTo(0.40);
    }

    @Test
    void of_multipleEvidences_takesMaxScore() {
        EvidenceItem e1 = new EvidenceItem("c1", "d1", 0.60, "content", "INQUIRY", null, null, null);
        EvidenceItem e2 = new EvidenceItem("c2", "d2", 0.90, "content", "INQUIRY", null, null, null);
        EvidenceItem e3 = new EvidenceItem("c3", "d3", 0.45, "content", "INQUIRY", null, null, null);

        PerQuestionEvidence result = PerQuestionEvidence.of(SQ, List.of(e1, e2, e3));

        assertThat(result.maxScore()).isEqualTo(0.90);
        assertThat(result.sufficient()).isTrue();
        assertThat(result.evidences()).hasSize(3);
    }

    @Test
    void of_subQuestionPreserved() {
        SubQuestion sq = new SubQuestion(2, "specific question", "naica");
        PerQuestionEvidence result = PerQuestionEvidence.of(sq, List.of());

        assertThat(result.subQuestion()).isEqualTo(sq);
        assertThat(result.subQuestion().index()).isEqualTo(2);
        assertThat(result.subQuestion().context()).isEqualTo("naica");
    }
}
