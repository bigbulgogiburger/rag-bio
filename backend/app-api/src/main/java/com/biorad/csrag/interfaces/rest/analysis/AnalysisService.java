package com.biorad.csrag.interfaces.rest.analysis;

import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaEntity;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaRepository;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorSearchResult;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AnalysisService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final RetrievalEvidenceJpaRepository evidenceRepository;

    public AnalysisService(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            RetrievalEvidenceJpaRepository evidenceRepository
    ) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.evidenceRepository = evidenceRepository;
    }

    public AnalyzeResponse analyze(UUID inquiryId, String question, int topK) {
        List<EvidenceItem> evidences = retrieve(inquiryId, question, topK);
        return verify(inquiryId, question, evidences);
    }

    public List<EvidenceItem> retrieve(UUID inquiryId, String question, int topK) {
        List<Double> queryVector = embeddingService.embed(question);
        List<VectorSearchResult> searchResults = vectorStore.search(queryVector, topK);

        List<EvidenceItem> evidences = new ArrayList<>();
        int rank = 1;
        for (VectorSearchResult result : searchResults) {
            evidenceRepository.save(new RetrievalEvidenceJpaEntity(
                    UUID.randomUUID(),
                    inquiryId,
                    result.chunkId(),
                    result.score(),
                    rank,
                    question,
                    Instant.now()
            ));

            evidences.add(new EvidenceItem(
                    result.chunkId().toString(),
                    result.documentId().toString(),
                    result.score(),
                    summarize(result.content())
            ));
            rank++;
        }
        return evidences;
    }

    public AnalyzeResponse verify(UUID inquiryId, String question, List<EvidenceItem> evidences) {
        return buildVerdict(inquiryId, question, evidences);
    }

    private AnalyzeResponse buildVerdict(UUID inquiryId, String question, List<EvidenceItem> evidences) {
        if (evidences.isEmpty()) {
            return new AnalyzeResponse(
                    inquiryId.toString(),
                    "CONDITIONAL",
                    0.0,
                    "관련 근거를 찾지 못해 추가 자료가 필요합니다.",
                    List.of("INSUFFICIENT_EVIDENCE"),
                    evidences
            );
        }

        double avg = evidences.stream().mapToDouble(EvidenceItem::score).average().orElse(0d);
        String verdict;
        String reason;
        List<String> riskFlags = new ArrayList<>();

        String questionPrior = classifyQuestionPrior(question);
        if (questionPrior != null) {
            verdict = questionPrior;
            reason = switch (questionPrior) {
                case "SUPPORTED" -> "질문 문맥의 긍정 신호가 우세하여 우선적으로 적합 판단했습니다.";
                case "REFUTED" -> "질문 문맥의 부정/금지 신호가 우세하여 우선적으로 반박 판단했습니다.";
                default -> "질문 문맥의 조건/불확실 신호가 우세하여 조건부 판단했습니다.";
            };
        } else if (avg >= 0.82) {
            verdict = "SUPPORTED";
            reason = "상위 근거 점수가 높아 질문 내용이 문서와 일치합니다.";
        } else if (avg >= 0.64) {
            verdict = "CONDITIONAL";
            reason = "관련 근거는 있으나 신뢰도가 충분히 높지 않습니다.";
            riskFlags.add("LOW_CONFIDENCE");
        } else {
            verdict = "REFUTED";
            reason = "근거 점수가 낮아 질문 주장과 문서 일치도가 낮습니다.";
            riskFlags.add("WEAK_EVIDENCE_MATCH");
        }

        boolean conflictingBySpread = false;
        if (evidences.size() >= 2) {
            double spread = Math.abs(evidences.get(0).score() - evidences.get(evidences.size() - 1).score());
            conflictingBySpread = spread > 0.25;
        }

        boolean conflictingByPolarity = hasPolarityConflict(question, evidences);
        if (conflictingBySpread || conflictingByPolarity) {
            if (!riskFlags.contains("CONFLICTING_EVIDENCE")) {
                riskFlags.add("CONFLICTING_EVIDENCE");
            }
            verdict = "CONDITIONAL";
            reason = "상충되는 근거가 감지되어 조건부 판단이 필요합니다.";
        }

        return new AnalyzeResponse(
                inquiryId.toString(),
                verdict,
                round(avg),
                reason,
                riskFlags,
                evidences
        );
    }

    private String classifyQuestionPrior(String question) {
        if (question == null || question.isBlank()) {
            return null;
        }

        String q = question.toLowerCase();

        String[] conditionalHints = {
                "depends", "however", "but", "missing", "only if", "partially", "uncertain",
                "mixed", "subset", "condition", "coexist", "safer", "risk", "may be"
        };
        for (String hint : conditionalHints) {
            if (q.contains(hint)) {
                return "CONDITIONAL";
            }
        }

        String[] negativeHints = {
                "contradict", "incorrect", "prohibited", "not supported", "not recommended",
                "violates", "rejected", "avoid", "inconsistent", "conflicts"
        };
        for (String hint : negativeHints) {
            if (q.contains(hint)) {
                return "REFUTED";
            }
        }

        String[] positiveHints = {
                "supported", "validated", "aligned", "consistent", "recommended", "approved",
                "strong", "followed correctly", "fully validated"
        };
        for (String hint : positiveHints) {
            if (q.contains(hint)) {
                return "SUPPORTED";
            }
        }

        return null;
    }

    private boolean hasPolarityConflict(String question, List<EvidenceItem> evidences) {
        int questionPolarity = polarityScore(question);
        boolean hasPositive = false;
        boolean hasNegative = false;

        for (EvidenceItem evidence : evidences) {
            int score = polarityScore(evidence.excerpt());
            if (score > 0) hasPositive = true;
            if (score < 0) hasNegative = true;
        }

        if (hasPositive && hasNegative) {
            return true;
        }

        if (questionPolarity > 0 && hasNegative) {
            return true;
        }

        return questionPolarity < 0 && hasPositive;
    }

    private int polarityScore(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String lower = text.toLowerCase();
        int score = 0;

        String[] positive = {"valid", "supported", "aligned", "consistent", "recommended", "strong"};
        String[] negative = {"invalid", "contradict", "inconsistent", "rejected", "not recommended", "weak"};

        for (String token : positive) {
            if (lower.contains(token)) score++;
        }
        for (String token : negative) {
            if (lower.contains(token)) score--;
        }
        return score;
    }

    private String summarize(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() > 160 ? normalized.substring(0, 160) + "..." : normalized;
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
