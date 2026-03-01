package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;

import java.util.List;

public interface CriticAgentService {

    CriticResult critique(String draft, String question, List<EvidenceItem> evidences);

    record CriticResult(
            double faithfulnessScore,
            List<ClaimVerification> claims,
            List<String> corrections,
            boolean needsRevision
    ) {
        public static CriticResult passing(double score) {
            return new CriticResult(score, List.of(), List.of(), false);
        }

        public static CriticResult failing(double score, List<ClaimVerification> claims, List<String> corrections) {
            return new CriticResult(score, claims, corrections, true);
        }
    }

    record ClaimVerification(
            String claim,
            String faithfulness,
            String citationAccuracy,
            String factualMatch
    ) {}
}
