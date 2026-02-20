package com.biorad.csrag.interfaces.rest.answer.agent;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApprovalAgentServiceTest {

    private ApprovalAgentService service;

    @BeforeEach
    void setUp() {
        service = new ApprovalAgentService();
    }

    private AnswerDraftJpaEntity mockDraft(double confidence, String riskFlags) {
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getConfidence()).thenReturn(confidence);
        when(draft.getRiskFlags()).thenReturn(riskFlags);
        return draft;
    }

    @Test
    void evaluate_allGatesPass_autoApproved() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, null);
        ReviewResult review = new ReviewResult("PASS", 90, List.of(), null, "Good");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("AUTO_APPROVED");
        assertThat(result.gateResults()).hasSize(4);
        assertThat(result.gateResults()).allMatch(GateResult::passed);
    }

    @Test
    void evaluate_lowConfidence_escalated() {
        AnswerDraftJpaEntity draft = mockDraft(0.5, null);
        ReviewResult review = new ReviewResult("PASS", 90, List.of(), null, "Good");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("ESCALATED");
        assertThat(result.reason()).contains("confidence");
    }

    @Test
    void evaluate_lowReviewScore_escalated() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, null);
        ReviewResult review = new ReviewResult("REVISE", 60, List.of(), null, "Needs work");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("ESCALATED");
        assertThat(result.reason()).contains("reviewScore");
    }

    @Test
    void evaluate_criticalIssue_rejected() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, null);
        List<ReviewIssue> issues = List.of(
                new ReviewIssue("ACCURACY", "CRITICAL", "Wrong info", "Fix it")
        );
        ReviewResult review = new ReviewResult("REJECT", 40, issues, null, "Critical issue");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("REJECTED");
        assertThat(result.reason()).contains("CRITICAL");
    }

    @Test
    void evaluate_safetyRiskFlag_escalated() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, "SAFETY_CONCERN");
        ReviewResult review = new ReviewResult("PASS", 90, List.of(), null, "Good");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("ESCALATED");
        assertThat(result.reason()).contains("noHighRiskFlags");
    }

    @Test
    void evaluate_regulatoryRiskFlag_escalated() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, "REGULATORY_RISK");
        ReviewResult review = new ReviewResult("PASS", 90, List.of(), null, "Good");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("ESCALATED");
    }

    @Test
    void evaluate_conflictingEvidenceFlag_escalated() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, "CONFLICTING_EVIDENCE");
        ReviewResult review = new ReviewResult("PASS", 90, List.of(), null, "Good");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("ESCALATED");
    }

    @Test
    void evaluate_emptyRiskFlags_passes() {
        AnswerDraftJpaEntity draft = mockDraft(0.9, "");
        ReviewResult review = new ReviewResult("PASS", 90, List.of(), null, "Good");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("AUTO_APPROVED");
    }

    @Test
    void evaluate_multipleFailedGates() {
        AnswerDraftJpaEntity draft = mockDraft(0.5, "SAFETY_CONCERN");
        ReviewResult review = new ReviewResult("REVISE", 60, List.of(), null, "Needs work");

        ApprovalResult result = service.evaluate(draft, review);

        assertThat(result.decision()).isEqualTo("ESCALATED");
        // Should list multiple failed gates
        assertThat(result.reason()).contains("confidence");
        assertThat(result.reason()).contains("reviewScore");
    }

    @Test
    void gateResult_recordAccessors() {
        GateResult gate = new GateResult("test", true, "0.8", ">= 0.7");
        assertThat(gate.gate()).isEqualTo("test");
        assertThat(gate.passed()).isTrue();
        assertThat(gate.actualValue()).isEqualTo("0.8");
        assertThat(gate.threshold()).isEqualTo(">= 0.7");
    }

    @Test
    void approvalResult_recordAccessors() {
        List<GateResult> gates = List.of(new GateResult("g1", true, "v", "e"));
        ApprovalResult result = new ApprovalResult("AUTO_APPROVED", "All passed", gates);
        assertThat(result.decision()).isEqualTo("AUTO_APPROVED");
        assertThat(result.reason()).isEqualTo("All passed");
        assertThat(result.gateResults()).hasSize(1);
    }

    @Test
    void reviewIssue_recordAccessors() {
        ReviewIssue issue = new ReviewIssue("TONE", "MEDIUM", "Too informal", "Use formal language");
        assertThat(issue.category()).isEqualTo("TONE");
        assertThat(issue.severity()).isEqualTo("MEDIUM");
        assertThat(issue.description()).isEqualTo("Too informal");
        assertThat(issue.suggestion()).isEqualTo("Use formal language");
    }
}
