package com.biorad.csrag.interfaces.rest.analysis;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaEntity;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaRepository;
import com.biorad.csrag.interfaces.rest.search.HybridSearchResult;
import com.biorad.csrag.interfaces.rest.search.HybridSearchService;
import com.biorad.csrag.interfaces.rest.search.QueryTranslationService;
import com.biorad.csrag.interfaces.rest.search.TranslatedQuery;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisService {

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final RetrievalEvidenceJpaRepository evidenceRepository;
    private final DocumentChunkJpaRepository chunkRepository;
    private final DocumentMetadataJpaRepository documentRepository;
    private final KnowledgeDocumentJpaRepository kbDocRepository;
    private final QueryTranslationService queryTranslationService;
    private final HybridSearchService hybridSearchService;

    public AnalysisService(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            RetrievalEvidenceJpaRepository evidenceRepository,
            DocumentChunkJpaRepository chunkRepository,
            DocumentMetadataJpaRepository documentRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            QueryTranslationService queryTranslationService,
            HybridSearchService hybridSearchService
    ) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.evidenceRepository = evidenceRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.kbDocRepository = kbDocRepository;
        this.queryTranslationService = queryTranslationService;
        this.hybridSearchService = hybridSearchService;
    }

    public AnalyzeResponse analyze(UUID inquiryId, String question, int topK) {
        TranslatedQuery tq = queryTranslationService.translate(question);
        List<EvidenceItem> evidences = doRetrieve(inquiryId, tq.translated(), topK);
        AnalyzeResponse response = verify(inquiryId, question, evidences);
        return new AnalyzeResponse(
                response.inquiryId(),
                response.verdict(),
                response.confidence(),
                response.reason(),
                response.riskFlags(),
                response.evidences(),
                tq.wasTranslated() ? tq.translated() : null
        );
    }

    public List<EvidenceItem> retrieve(UUID inquiryId, String question, int topK) {
        TranslatedQuery tq = queryTranslationService.translate(question);
        return doRetrieve(inquiryId, tq.translated(), topK);
    }

    private List<EvidenceItem> doRetrieve(UUID inquiryId, String searchQuery, int topK) {
        List<HybridSearchResult> searchResults = hybridSearchService.search(searchQuery, topK);

        // 배치 조회로 N+1 방지
        Set<UUID> chunkIds = searchResults.stream().map(HybridSearchResult::chunkId).collect(Collectors.toSet());
        Set<UUID> docIds = searchResults.stream().map(HybridSearchResult::documentId).collect(Collectors.toSet());

        Map<UUID, DocumentChunkJpaEntity> chunkMap = chunkRepository.findAllById(chunkIds)
                .stream().collect(Collectors.toMap(DocumentChunkJpaEntity::getId, c -> c));

        // sourceId 기반 조회를 위해 청크의 sourceId 수집
        Set<UUID> sourceIds = chunkMap.values().stream()
                .map(DocumentChunkJpaEntity::getSourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> allLookupIds = new HashSet<>(docIds);
        allLookupIds.addAll(sourceIds);

        Map<UUID, String> fileNameMap = new HashMap<>();
        documentRepository.findAllById(allLookupIds)
                .forEach(d -> fileNameMap.put(d.getId(), d.getFileName()));
        kbDocRepository.findAllById(allLookupIds)
                .forEach(d -> fileNameMap.put(d.getId(), d.getFileName()));

        List<EvidenceItem> evidences = new ArrayList<>();
        int rank = 1;
        for (HybridSearchResult result : searchResults) {
            evidenceRepository.save(new RetrievalEvidenceJpaEntity(
                    UUID.randomUUID(),
                    inquiryId,
                    result.chunkId(),
                    result.vectorScore(),
                    rank,
                    searchQuery,
                    Instant.now()
            ));

            DocumentChunkJpaEntity chunk = chunkMap.get(result.chunkId());
            String fileName = fileNameMap.get(result.documentId());
            // documentId로 찾지 못하면 청크의 sourceId로 재조회
            if (fileName == null && chunk != null && chunk.getSourceId() != null) {
                fileName = fileNameMap.get(chunk.getSourceId());
            }
            Integer pageStart = chunk != null ? chunk.getPageStart() : null;
            Integer pageEnd = chunk != null ? chunk.getPageEnd() : null;

            evidences.add(new EvidenceItem(
                    result.chunkId().toString(),
                    result.documentId().toString(),
                    result.vectorScore(),
                    summarize(result.content()),
                    result.sourceType(),
                    fileName,
                    pageStart,
                    pageEnd
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
                    evidences,
                    null
            );
        }

        double avg = evidences.stream().mapToDouble(EvidenceItem::score).average().orElse(0d);
        String verdict;
        String reason;
        List<String> riskFlags = new ArrayList<>();

        if (avg >= 0.70) {
            verdict = "SUPPORTED";
            reason = "상위 근거 점수가 높아 질문 내용이 문서와 일치합니다.";
        } else if (avg >= 0.45) {
            verdict = "CONDITIONAL";
            reason = "관련 근거는 있으나 신뢰도가 충분히 높지 않습니다.";
        } else {
            verdict = "REFUTED";
            reason = "근거 점수가 낮아 질문 주장과 문서 일치도가 낮습니다.";
            riskFlags.add("WEAK_EVIDENCE_MATCH");
            if (avg < 0.50) {
                riskFlags.add("LOW_CONFIDENCE");
            }
        }

        boolean conflictingBySpread = false;
        if (evidences.size() >= 2) {
            double spread = Math.abs(evidences.get(0).score() - evidences.get(evidences.size() - 1).score());
            conflictingBySpread = spread > 0.35;
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
                evidences,
                null
        );
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
        return content.replaceAll("\\s+", " ").trim();
    }

    private double round(double value) {
        return Math.round(value * 1000d) / 1000d;
    }
}
