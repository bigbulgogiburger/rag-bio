package com.biorad.csrag.interfaces.rest.answer.agent;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ApprovalAgentService {

    public ApprovalResult evaluate(AnswerDraftJpaEntity draft, ReviewResult review) {
        List<GateResult> gates = new ArrayList<>();

        boolean confidencePass = draft.getConfidence() >= 0.7;
        gates.add(new GateResult(
                "confidence",
                confidencePass,
                String.valueOf(draft.getConfidence()),
                ">= 0.7"
        ));

        boolean scorePass = review.score() >= 80;
        gates.add(new GateResult(
                "reviewScore",
                scorePass,
                String.valueOf(review.score()),
                ">= 80"
        ));

        boolean hasCritical = review.issues() != null && review.issues().stream()
                .anyMatch(issue -> "CRITICAL".equalsIgnoreCase(issue.severity()));
        gates.add(new GateResult(
                "noCriticalIssues",
                !hasCritical,
                hasCritical ? "CRITICAL issues found" : "No CRITICAL issues",
                "No CRITICAL severity issues"
        ));

        String riskFlags = draft.getRiskFlags();
        boolean hasHighRiskFlags = riskFlags != null && !riskFlags.isBlank()
                && (riskFlags.contains("SAFETY_CONCERN") || riskFlags.contains("REGULATORY_RISK") || riskFlags.contains("CONFLICTING_EVIDENCE"));
        gates.add(new GateResult(
                "noHighRiskFlags",
                !hasHighRiskFlags,
                hasHighRiskFlags ? riskFlags : "None",
                "No SAFETY/REGULATORY/CONFLICTING risk flags"
        ));

        boolean allPassed = gates.stream().allMatch(GateResult::passed);

        String decision;
        String reason;

        if (hasCritical) {
            decision = "REJECTED";
            reason = "CRITICAL 심각도 이슈가 발견되어 답변 초안이 거부되었습니다.";
        } else if (allPassed) {
            decision = "AUTO_APPROVED";
            reason = "모든 품질 게이트를 통과하여 자동 승인되었습니다.";
        } else {
            decision = "ESCALATED";
            List<String> failedGates = gates.stream()
                    .filter(g -> !g.passed())
                    .map(GateResult::gate)
                    .toList();
            reason = "다음 품질 게이트를 통과하지 못하여 사람의 검토가 필요합니다: " + String.join(", ", failedGates);
        }

        return new ApprovalResult(decision, reason, gates);
    }
}
