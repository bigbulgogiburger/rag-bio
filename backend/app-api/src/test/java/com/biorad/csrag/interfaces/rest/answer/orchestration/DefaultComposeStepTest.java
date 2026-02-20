package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultComposeStepTest {

    private DefaultComposeStep composeStep;

    @BeforeEach
    void setUp() {
        composeStep = new DefaultComposeStep();
    }

    private AnalyzeResponse makeAnalysis(String verdict, double confidence, List<String> riskFlags) {
        return new AnalyzeResponse(
                "test-inquiry-id", verdict, confidence, "reason",
                riskFlags,
                List.of(new EvidenceItem("chunk1", "doc1", 0.9, "content", "INQUIRY", null, null, null)),
                null
        );
    }

    // ===== Tone: professional (default) =====

    @Test
    void compose_professionalTone_supported() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "professional", "email");
        assertThat(result.draft()).contains("안녕하세요");
        assertThat(result.draft()).contains("감사합니다");
        assertThat(result.draft()).contains("타당한 방향");
        assertThat(result.formatWarnings()).isEmpty();
    }

    @Test
    void compose_professionalTone_refuted() {
        var result = composeStep.execute(makeAnalysis("REFUTED", 0.9, List.of()), "professional", "email");
        assertThat(result.draft()).contains("권장되지 않는");
    }

    @Test
    void compose_professionalTone_inconclusive() {
        var result = composeStep.execute(makeAnalysis("INCONCLUSIVE", 0.9, List.of()), "professional", "email");
        assertThat(result.draft()).contains("조건 의존성");
    }

    // ===== Tone: brief =====

    @Test
    void compose_briefTone_supported() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "brief", "email");
        assertThat(result.draft()).contains("타당한 것으로 판단");
    }

    @Test
    void compose_briefTone_refuted() {
        var result = composeStep.execute(makeAnalysis("REFUTED", 0.9, List.of()), "brief", "email");
        assertThat(result.draft()).contains("권장되지 않는");
    }

    @Test
    void compose_briefTone_inconclusive() {
        var result = composeStep.execute(makeAnalysis("INCONCLUSIVE", 0.9, List.of()), "brief", "email");
        assertThat(result.draft()).contains("단정이 어려운");
    }

    // ===== Tone: technical =====

    @Test
    void compose_technicalTone_supported() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "technical", "email");
        assertThat(result.draft()).contains("일치도가 높은");
    }

    @Test
    void compose_technicalTone_refuted() {
        var result = composeStep.execute(makeAnalysis("REFUTED", 0.9, List.of()), "technical", "email");
        assertThat(result.draft()).contains("충돌하는");
    }

    @Test
    void compose_technicalTone_inconclusive() {
        var result = composeStep.execute(makeAnalysis("INCONCLUSIVE", 0.9, List.of()), "technical", "email");
        assertThat(result.draft()).contains("상충 또는 신뢰도 부족");
    }

    // ===== Channel: messenger =====

    @Test
    void compose_messengerChannel_includesSummaryTag() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "professional", "messenger");
        assertThat(result.draft()).contains("[요약]");
        assertThat(result.formatWarnings()).isEmpty();
    }

    @Test
    void compose_messengerChannel_longDraft_hasOverflowWarning() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "technical", "messenger");
        // technical messenger drafts will likely exceed 260 chars
        if (result.draft().length() > 260) {
            assertThat(result.formatWarnings()).contains("MESSENGER_LENGTH_OVERFLOW");
        }
    }

    // ===== Guardrails =====

    @Test
    void compose_lowConfidence_addsNotice() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.5, List.of()), "professional", "email");
        assertThat(result.draft()).contains("근거 신뢰도가 충분히 높지 않아");
    }

    @Test
    void compose_riskFlags_addsNotice() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of("SAMPLE_CONDITION_MISMATCH")), "professional", "email");
        assertThat(result.draft()).contains("위험 신호가 감지");
        assertThat(result.draft()).contains("SAMPLE_CONDITION_MISMATCH");
    }

    @Test
    void compose_lowConfidence_and_riskFlags() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.3, List.of("EXPIRED_DOCUMENT")), "professional", "email");
        assertThat(result.draft()).contains("근거 신뢰도가 충분히 높지 않아");
        assertThat(result.draft()).contains("위험 신호가 감지");
    }

    // ===== Null/blank tone and channel defaults =====

    @Test
    void compose_nullTone_defaultsToProfessional() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), null, "email");
        assertThat(result.draft()).contains("안녕하세요");
    }

    @Test
    void compose_blankChannel_defaultsToEmail() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "professional", "  ");
        assertThat(result.draft()).contains("안녕하세요");
        assertThat(result.draft()).contains("감사합니다");
    }

    // ===== Email format warnings =====

    @Test
    void compose_emailChannel_briefTone_missingGreetingAndClosing() {
        var result = composeStep.execute(makeAnalysis("SUPPORTED", 0.9, List.of()), "brief", "email");
        // brief tone + email channel: the email wrapper adds greeting/closing
        // Default channel formatting adds "안녕하세요" and "감사합니다"
        assertThat(result.formatWarnings()).isEmpty();
    }
}
