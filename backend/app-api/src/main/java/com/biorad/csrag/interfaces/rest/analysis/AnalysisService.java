package com.biorad.csrag.interfaces.rest.analysis;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaEntity;
import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaEntity;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaEntity;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaRepository;
import com.biorad.csrag.interfaces.rest.answer.orchestration.PerQuestionEvidence;
import com.biorad.csrag.interfaces.rest.answer.orchestration.SubQuestion;
import com.biorad.csrag.interfaces.rest.search.HybridSearchResult;
import com.biorad.csrag.interfaces.rest.search.HybridSearchService;
import com.biorad.csrag.interfaces.rest.search.QueryTranslationService;
import com.biorad.csrag.interfaces.rest.search.RerankingService;
import com.biorad.csrag.interfaces.rest.search.SearchFilter;
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
    private final RerankingService rerankingService;

    public AnalysisService(
            EmbeddingService embeddingService,
            VectorStore vectorStore,
            RetrievalEvidenceJpaRepository evidenceRepository,
            DocumentChunkJpaRepository chunkRepository,
            DocumentMetadataJpaRepository documentRepository,
            KnowledgeDocumentJpaRepository kbDocRepository,
            QueryTranslationService queryTranslationService,
            HybridSearchService hybridSearchService,
            RerankingService rerankingService
    ) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.evidenceRepository = evidenceRepository;
        this.chunkRepository = chunkRepository;
        this.documentRepository = documentRepository;
        this.kbDocRepository = kbDocRepository;
        this.queryTranslationService = queryTranslationService;
        this.hybridSearchService = hybridSearchService;
        this.rerankingService = rerankingService;
    }

    public AnalyzeResponse analyze(UUID inquiryId, String question, int topK) {
        TranslatedQuery tq = queryTranslationService.translate(question);
        List<EvidenceItem> evidences = doRetrieve(inquiryId, tq.translated(), topK, SearchFilter.none());
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
        return doRetrieve(inquiryId, tq.translated(), topK, SearchFilter.none());
    }

    public List<EvidenceItem> retrieve(UUID inquiryId, String question, int topK, SearchFilter filter) {
        TranslatedQuery tq = queryTranslationService.translate(question);
        return doRetrieve(inquiryId, tq.translated(), topK, filter);
    }

    public List<PerQuestionEvidence> retrievePerQuestion(UUID inquiryId, List<SubQuestion> subQuestions, int topK, SearchFilter filter) {
        List<PerQuestionEvidence> results = new ArrayList<>();
        for (SubQuestion sq : subQuestions) {
            SearchFilter sqFilter = (!sq.productFamilies().isEmpty())
                    ? SearchFilter.forProducts(filter.inquiryId(), sq.productFamilies())
                    : filter;
            List<EvidenceItem> evidences = retrieve(inquiryId, sq.question(), topK, sqFilter);
            results.add(PerQuestionEvidence.of(sq, evidences));
        }
        return results;
    }

    private List<EvidenceItem> doRetrieve(UUID inquiryId, String searchQuery, int topK, SearchFilter filter) {
        // 리랭킹을 위해 더 많은 후보 검색 (topK * 5)
        int candidateCount = topK * 5;
        List<HybridSearchResult> searchResults = hybridSearchService.search(searchQuery, candidateCount, filter);

        // Cross-Encoder 리랭킹
        List<RerankingService.RerankResult> reranked = rerankingService.rerank(searchQuery, searchResults, topK);

        // 배치 조회로 N+1 방지
        Set<UUID> chunkIds = reranked.stream().map(RerankingService.RerankResult::chunkId).collect(Collectors.toSet());
        Set<UUID> docIds = reranked.stream().map(RerankingService.RerankResult::documentId).collect(Collectors.toSet());

        Map<UUID, DocumentChunkJpaEntity> chunkMap = chunkRepository.findAllById(chunkIds)
                .stream().collect(Collectors.toMap(DocumentChunkJpaEntity::getId, c -> c));

        // Parent-Child: CHILD 청크의 부모 청크를 배치 조회
        Set<UUID> parentIds = chunkMap.values().stream()
                .filter(c -> "CHILD".equals(c.getChunkLevel()) && c.getParentChunkId() != null)
                .map(DocumentChunkJpaEntity::getParentChunkId)
                .collect(Collectors.toSet());

        Map<UUID, DocumentChunkJpaEntity> parentMap = parentIds.isEmpty()
                ? Map.of()
                : chunkRepository.findAllById(parentIds).stream()
                    .collect(Collectors.toMap(DocumentChunkJpaEntity::getId, c -> c));

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
        for (RerankingService.RerankResult result : reranked) {
            evidenceRepository.save(new RetrievalEvidenceJpaEntity(
                    UUID.randomUUID(),
                    inquiryId,
                    result.chunkId(),
                    result.rerankScore(),
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
            String productFamily = chunk != null ? chunk.getProductFamily() : null;

            // Parent-Child: CHILD 청크면 PARENT 콘텐츠를 LLM에 제공 (더 넓은 문맥)
            String contentForLlm = result.content();
            if (chunk != null && "CHILD".equals(chunk.getChunkLevel()) && chunk.getParentChunkId() != null) {
                DocumentChunkJpaEntity parent = parentMap.get(chunk.getParentChunkId());
                if (parent != null) {
                    contentForLlm = parent.getContent();
                }
            }

            evidences.add(new EvidenceItem(
                    result.chunkId().toString(),
                    result.documentId().toString(),
                    result.rerankScore(),
                    summarize(contentForLlm),
                    result.sourceType(),
                    fileName,
                    pageStart,
                    pageEnd,
                    productFamily
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

        // Position-weighted scoring: 상위 순위에 더 높은 가중치 (1위: 1.0, 2위: 0.5, 3위: 0.33, ...)
        double weightedScore = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < evidences.size(); i++) {
            double weight = 1.0 / (i + 1);
            weightedScore += evidences.get(i).score() * weight;
            weightSum += weight;
        }
        double avg = weightSum > 0 ? weightedScore / weightSum : 0.0;
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
