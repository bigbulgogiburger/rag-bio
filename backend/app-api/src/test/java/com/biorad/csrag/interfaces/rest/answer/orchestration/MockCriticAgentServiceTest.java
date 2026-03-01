package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MockCriticAgentServiceTest {

    private final MockCriticAgentService service = new MockCriticAgentService();

    @Test
    void critique_alwaysReturnsPassingResult() {
        EvidenceItem evidence = new EvidenceItem("c1", "d1", 0.9, "AAV2 compatible with ddPCR",
                "INQUIRY", "manual.pdf", 5, 5);

        CriticAgentService.CriticResult result = service.critique(
                "AAV2는 ddPCR과 호환됩니다.",
                "AAV2 호환성 문의",
                List.of(evidence));

        assertThat(result.needsRevision()).isFalse();
        assertThat(result.faithfulnessScore()).isEqualTo(1.0);
        assertThat(result.corrections()).isEmpty();
    }

    @Test
    void critique_withEmptyEvidences_stillPasses() {
        CriticAgentService.CriticResult result = service.critique("답변", "질문", List.of());

        assertThat(result.needsRevision()).isFalse();
        assertThat(result.faithfulnessScore()).isEqualTo(1.0);
    }
}
