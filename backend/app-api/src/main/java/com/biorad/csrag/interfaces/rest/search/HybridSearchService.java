package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorSearchResult;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final KeywordSearchService keywordSearchService;
    private final DocumentMetadataJpaRepository documentRepository;
    private final HydeQueryTransformer hydeQueryTransformer;

    @Value("${search.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${search.hybrid.rrf-k:60}")
    private int rrfK;

    @Value("${search.hybrid.vector-weight:1.0}")
    private double vectorWeight;

    @Value("${search.hybrid.keyword-weight:1.0}")
    private double keywordWeight;

    @Value("${search.hybrid.min-vector-score:0.25}")
    private double minVectorScore;

    public HybridSearchService(EmbeddingService embeddingService,
                               VectorStore vectorStore,
                               KeywordSearchService keywordSearchService,
                               DocumentMetadataJpaRepository documentRepository,
                               HydeQueryTransformer hydeQueryTransformer) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.keywordSearchService = keywordSearchService;
        this.documentRepository = documentRepository;
        this.hydeQueryTransformer = hydeQueryTransformer;
    }

    public List<HybridSearchResult> search(String query, int topK) {
        return search(query, topK, null);
    }

    public List<HybridSearchResult> search(String query, int topK, SearchFilter filter) {
        // 벡터 검색용 필터: inquiryId → documentIds 해소 (벡터 DB는 SQL 서브쿼리 불가)
        SearchFilter vectorFilter = resolveForVectorSearch(filter);
        List<Double> queryVector = hydeQueryTransformer.isEnabled()
                ? hydeQueryTransformer.transformAndEmbed(query, "")
                : embeddingService.embedQuery(query);
        List<VectorSearchResult> vectorResults = (vectorFilter != null && !vectorFilter.isEmpty())
                ? vectorStore.search(queryVector, topK * 2, vectorFilter)
                : vectorStore.search(queryVector, topK * 2);

        if (!hybridEnabled) {
            log.info("hybrid.search query={} vector={} keyword=0 fused={} (vector-only mode)",
                    query, vectorResults.size(), vectorResults.size());
            return vectorResults.stream()
                    .limit(topK)
                    .map(vr -> new HybridSearchResult(
                            vr.chunkId(), vr.documentId(), vr.content(),
                            vr.score(), 0.0, vr.score(),
                            vr.sourceType(), "VECTOR"))
                    .toList();
        }

        List<KeywordSearchResult> keywordResults = (filter != null && !filter.isEmpty())
                ? keywordSearchService.search(query, topK * 2, filter)
                : keywordSearchService.search(query, topK * 2);

        Map<UUID, RrfEntry> rrfMap = new LinkedHashMap<>();

        for (int rank = 0; rank < vectorResults.size(); rank++) {
            VectorSearchResult vr = vectorResults.get(rank);
            RrfEntry entry = rrfMap.computeIfAbsent(vr.chunkId(),
                    id -> new RrfEntry(vr.chunkId(), vr.documentId(), vr.content(), vr.sourceType()));
            entry.vectorScore = vr.score();
            entry.fusedScore += vectorWeight * (1.0 / (rrfK + rank + 1));
            entry.addSource("VECTOR");
        }

        for (int rank = 0; rank < keywordResults.size(); rank++) {
            KeywordSearchResult kr = keywordResults.get(rank);
            RrfEntry entry = rrfMap.computeIfAbsent(kr.chunkId(),
                    id -> new RrfEntry(kr.chunkId(), kr.documentId(), kr.content(), kr.sourceType()));
            entry.keywordScore = kr.score();
            entry.fusedScore += keywordWeight * (1.0 / (rrfK + rank + 1));
            entry.addSource("KEYWORD");
        }

        List<HybridSearchResult> fused = rrfMap.values().stream()
                .sorted(Comparator.comparingDouble((RrfEntry e) -> e.fusedScore).reversed())
                .limit(topK)
                .map(e -> new HybridSearchResult(
                        e.chunkId, e.documentId, e.content,
                        e.vectorScore, e.keywordScore, e.fusedScore,
                        e.sourceType, e.getMatchSource()))
                .collect(Collectors.toList());

        List<HybridSearchResult> filtered = fused.stream()
                .filter(r -> r.vectorScore() == 0.0 || r.vectorScore() >= minVectorScore)
                .collect(Collectors.toList());

        if (filtered.isEmpty() && !fused.isEmpty()) {
            log.info("hybrid.search all results below min-vector-score={}, returning unfiltered", minVectorScore);
            filtered = fused;
        }

        log.info("hybrid.search query={} vector={} keyword={} fused={} filtered={} filter={}",
                query, vectorResults.size(), keywordResults.size(), fused.size(), filtered.size(), filter);

        return filtered;
    }

    /**
     * 벡터 검색용 필터 해소: inquiryId만 있고 documentIds가 없으면
     * DB에서 해당 문의 문서 ID를 조회하여 documentIds + sourceTypes(KNOWLEDGE_BASE)로 변환.
     * 벡터 DB는 SQL 서브쿼리를 지원하지 않으므로 여기서 미리 해소한다.
     */
    private SearchFilter resolveForVectorSearch(SearchFilter filter) {
        if (filter == null || filter.inquiryId() == null || filter.hasDocumentFilter()) {
            return filter;
        }
        Set<UUID> inquiryDocIds = documentRepository
                .findByInquiryIdOrderByCreatedAtDesc(filter.inquiryId())
                .stream()
                .map(DocumentMetadataJpaEntity::getId)
                .collect(Collectors.toSet());

        // 문의 문서 ID + KB 소스 타입으로 필터 생성
        // VectorStore에서 OR 로직 적용: documentIds에 매칭 OR sourceType=KNOWLEDGE_BASE
        Set<String> sourceTypes = filter.hasSourceTypeFilter()
                ? filter.sourceTypes()
                : Set.of("KNOWLEDGE_BASE");

        return new SearchFilter(filter.inquiryId(), inquiryDocIds, filter.productFamilies(), sourceTypes);
    }

    private static class RrfEntry {
        final UUID chunkId;
        final UUID documentId;
        final String content;
        final String sourceType;
        double vectorScore;
        double keywordScore;
        double fusedScore;
        private final Set<String> sources = new LinkedHashSet<>();

        RrfEntry(UUID chunkId, UUID documentId, String content, String sourceType) {
            this.chunkId = chunkId;
            this.documentId = documentId;
            this.content = content;
            this.sourceType = sourceType;
        }

        void addSource(String source) {
            sources.add(source);
        }

        String getMatchSource() {
            if (sources.contains("VECTOR") && sources.contains("KEYWORD")) {
                return "VECTOR+KEYWORD";
            }
            return sources.iterator().next();
        }
    }
}
