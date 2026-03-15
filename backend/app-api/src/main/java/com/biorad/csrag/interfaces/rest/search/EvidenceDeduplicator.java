package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Removes duplicate and near-duplicate evidence items from search results.
 * Applied after hybrid search + reranking, before quality gating.
 */
@Component
public class EvidenceDeduplicator {

    private static final Logger log = LoggerFactory.getLogger(EvidenceDeduplicator.class);
    private static final double JACCARD_THRESHOLD = 0.7;
    private static final Pattern TOKEN_SPLITTER = Pattern.compile("[\\s\\p{Punct}]+");

    /**
     * Remove duplicate and near-duplicate evidence items.
     * Strategy:
     * 1. Remove exact chunkId duplicates (keep highest score)
     * 2. Remove content near-duplicates (Jaccard similarity > 0.7, keep highest score)
     * 3. Remove same-document, same-page-range duplicates (keep highest score)
     */
    public List<EvidenceItem> deduplicate(List<EvidenceItem> items) {
        if (items == null || items.size() <= 1) {
            return items == null ? List.of() : items;
        }

        int originalSize = items.size();

        // Step 1: chunkId dedup — keep highest score per chunkId
        Map<String, EvidenceItem> byChunkId = new LinkedHashMap<>();
        for (EvidenceItem item : items) {
            byChunkId.merge(item.chunkId(), item, (a, b) -> a.score() >= b.score() ? a : b);
        }
        List<EvidenceItem> deduped = new ArrayList<>(byChunkId.values());
        int afterChunkDedup = deduped.size();

        // Step 2: Jaccard similarity dedup
        deduped = removeNearDuplicates(deduped);
        int afterJaccardDedup = deduped.size();

        // Step 3: same document + overlapping page range
        deduped = removeSameDocPageDuplicates(deduped);
        int afterPageDedup = deduped.size();

        log.debug("EvidenceDeduplicator: {} → {} (chunkId: -{}, jaccard: -{}, page: -{})",
                originalSize, afterPageDedup,
                originalSize - afterChunkDedup,
                afterChunkDedup - afterJaccardDedup,
                afterJaccardDedup - afterPageDedup);

        return deduped;
    }

    /**
     * Remove near-duplicates by Jaccard similarity on word tokens.
     * For each pair where similarity > threshold, keep the one with higher score.
     */
    List<EvidenceItem> removeNearDuplicates(List<EvidenceItem> items) {
        if (items.size() <= 1) return items;

        // Sort by score descending so higher-scored items are kept
        List<EvidenceItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingDouble(EvidenceItem::score).reversed());

        List<EvidenceItem> result = new ArrayList<>();
        for (EvidenceItem candidate : sorted) {
            boolean isDuplicate = false;
            for (EvidenceItem kept : result) {
                if (jaccardSimilarity(candidate.excerpt(), kept.excerpt()) > JACCARD_THRESHOLD) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * Remove same-document, overlapping-page-range duplicates.
     * When two evidence items come from the same document and share overlapping page ranges,
     * keep only the higher-scored one.
     */
    List<EvidenceItem> removeSameDocPageDuplicates(List<EvidenceItem> items) {
        if (items.size() <= 1) return items;

        // Sort by score descending
        List<EvidenceItem> sorted = new ArrayList<>(items);
        sorted.sort(Comparator.comparingDouble(EvidenceItem::score).reversed());

        List<EvidenceItem> result = new ArrayList<>();
        for (EvidenceItem candidate : sorted) {
            boolean isDuplicate = false;
            if (candidate.documentId() != null
                    && candidate.pageStart() != null
                    && candidate.pageEnd() != null) {
                for (EvidenceItem kept : result) {
                    if (candidate.documentId().equals(kept.documentId())
                            && kept.pageStart() != null
                            && kept.pageEnd() != null
                            && pagesOverlap(candidate.pageStart(), candidate.pageEnd(),
                                            kept.pageStart(), kept.pageEnd())) {
                        isDuplicate = true;
                        break;
                    }
                }
            }
            if (!isDuplicate) {
                result.add(candidate);
            }
        }
        return result;
    }

    double jaccardSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        Set<String> setA = tokenize(a);
        Set<String> setB = tokenize(b);
        if (setA.isEmpty() && setB.isEmpty()) return 1.0;
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] tokens = TOKEN_SPLITTER.split(text.toLowerCase(Locale.ROOT));
        Set<String> result = new HashSet<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                result.add(token);
            }
        }
        return result;
    }

    private boolean pagesOverlap(int startA, int endA, int startB, int endB) {
        return startA <= endB && startB <= endA;
    }
}
