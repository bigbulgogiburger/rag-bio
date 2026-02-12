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

        return buildVerdict(inquiryId, evidences);
    }

    private AnalyzeResponse buildVerdict(UUID inquiryId, List<EvidenceItem> evidences) {
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

        if (avg >= 0.85) {
            verdict = "SUPPORTED";
            reason = "상위 근거 점수가 높아 질문 내용이 문서와 일치합니다.";
        } else if (avg >= 0.70) {
            verdict = "CONDITIONAL";
            reason = "관련 근거는 있으나 신뢰도가 충분히 높지 않습니다.";
            riskFlags.add("LOW_CONFIDENCE");
        } else {
            verdict = "REFUTED";
            reason = "근거 점수가 낮아 질문 주장과 문서 일치도가 낮습니다.";
            riskFlags.add("WEAK_EVIDENCE_MATCH");
        }

        if (evidences.size() >= 2) {
            double spread = Math.abs(evidences.get(0).score() - evidences.get(evidences.size() - 1).score());
            if (spread > 0.25) {
                riskFlags.add("CONFLICTING_EVIDENCE");
            }
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
