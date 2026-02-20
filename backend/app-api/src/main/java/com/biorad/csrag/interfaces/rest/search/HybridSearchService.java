package com.biorad.csrag.interfaces.rest.search;

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

    @Value("${search.hybrid.enabled:true}")
    private boolean hybridEnabled;

    @Value("${search.hybrid.rrf-k:60}")
    private int rrfK;

    @Value("${search.hybrid.vector-weight:1.0}")
    private double vectorWeight;

    @Value("${search.hybrid.keyword-weight:1.0}")
    private double keywordWeight;

    public HybridSearchService(EmbeddingService embeddingService,
                               VectorStore vectorStore,
                               KeywordSearchService keywordSearchService) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.keywordSearchService = keywordSearchService;
    }

    public List<HybridSearchResult> search(String query, int topK) {
        List<Double> queryVector = embeddingService.embed(query);
        List<VectorSearchResult> vectorResults = vectorStore.search(queryVector, topK * 2);

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

        List<KeywordSearchResult> keywordResults = keywordSearchService.search(query, topK * 2);

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

        log.info("hybrid.search query={} vector={} keyword={} fused={}",
                query, vectorResults.size(), keywordResults.size(), fused.size());

        return fused;
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
