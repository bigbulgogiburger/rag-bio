package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SelfReviewStepTest {

    private SelfReviewStep selfReviewStep;

    @BeforeEach
    void setUp() {
        selfReviewStep = new SelfReviewStep();
    }

    @Test
    void review_cleanDraft_passes() {
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "장비 스펙 안내", "INQUIRY", "manual.pdf", 1, 3)
        );
        String draft = "장비 스펙에 대한 안내입니다. 사내 자료를 참고한 결과 타당합니다.";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, evidences, "장비 스펙을 알려주세요");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void review_nullDraft_passes() {
        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(null, List.of(), "question");

        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void review_emptyEvidences_passes() {
        SelfReviewStep.SelfReviewResult result = selfReviewStep.review("some draft text", List.of(), "question");

        assertThat(result.passed()).isTrue();
    }

    @Test
    void review_detectsDuplicateSentences() {
        String draft = "CFX96 장비의 온도 설정은 95도입니다.\n" +
                "프로토콜에 따라 진행하세요.\n" +
                "CFX96 장비의 온도 설정은 95도입니다.";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, List.of(), "question");

        assertThat(result.issues()).anyMatch(i -> "DUPLICATION".equals(i.category()));
    }

    @Test
    void review_detectsPhantomProduct() {
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "CFX96 장비 매뉴얼 내용", "INQUIRY", "manual.pdf", 1, 3)
        );
        String draft = "QX200 장비에 대한 안내입니다. CFX96 설정도 확인하세요.";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, evidences, "question");

        assertThat(result.issues()).anyMatch(i ->
                "INCONSISTENCY".equals(i.category()) && i.description().toLowerCase().contains("qx200"));
    }

    @Test
    void review_detectsProcedureIncompleteness() {
        String draft = "온도 조건은 95도로 설정하는 경우에 해당합니다.";
        String question = "어떻게 설정하나요? 방법을 알려주세요.";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, List.of(), question);

        assertThat(result.issues()).anyMatch(i -> "INCOMPLETE_PROCEDURE".equals(i.category()));
    }

    @Test
    void review_procedurePresent_noProcedureIssue() {
        String draft = "먼저 온도 조건을 95도로 설정한 다음 진행합니다.";
        String question = "어떻게 설정하나요?";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, List.of(), question);

        assertThat(result.issues()).noneMatch(i -> "INCOMPLETE_PROCEDURE".equals(i.category()));
    }

    @Test
    void review_detectsCitationMismatch() {
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "content", "INQUIRY", "real-manual.pdf", 5, 10)
        );
        String draft = "결과를 확인하세요. (wrong-file.pdf, p.1)";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, evidences, "question");

        assertThat(result.issues()).anyMatch(i -> "CITATION_MISMATCH".equals(i.category()));
    }

    @Test
    void review_validCitation_noCitationIssue() {
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "content", "INQUIRY", "manual.pdf", 1, 5)
        );
        String draft = "사내 자료를 참고한 결과 (manual.pdf, p.3) 타당합니다.";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, evidences, "question");

        assertThat(result.issues()).noneMatch(i -> "CITATION_MISMATCH".equals(i.category()));
    }

    @Test
    void review_criticalIssue_failsReview() {
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "CFX96 관련 내용", "INQUIRY", "manual.pdf", 1, 3)
        );
        // Mention a product not in evidence alongside the one that is
        String draft = "QX200 장비에 대한 안내입니다.";

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, evidences, "question");

        assertThat(result.passed()).isFalse();
        assertThat(result.feedback()).isNotBlank();
    }

    @Test
    void review_onlyWarnings_passes() {
        String draft = "사내 자료를 참고한 결과 (wrong.pdf, p.99) 확인되었습니다.";
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("c1", "d1", 0.9, "content", "INQUIRY", "real.pdf", 1, 5)
        );

        SelfReviewStep.SelfReviewResult result = selfReviewStep.review(draft, evidences, "question");

        // CITATION_MISMATCH is WARNING severity, not CRITICAL - should still pass
        assertThat(result.passed()).isTrue();
        assertThat(result.issues()).isNotEmpty();
    }
}
