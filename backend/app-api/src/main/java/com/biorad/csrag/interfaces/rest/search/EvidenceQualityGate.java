package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Filters evidence by quality criteria: dedup, min score, per-document limit,
 * source diversity, and max items.
 */
@Component
public class EvidenceQualityGate {

    private static final Logger log = LoggerFactory.getLogger(EvidenceQualityGate.class);

    @Value("${rag.evidence.max-items:8}")
    private int maxItems;

    @Value("${rag.evidence.min-score:0.30}")
    private double minScore;

    @Value("${rag.evidence.max-per-document:3}")
    private int maxPerDocument;

    @Value("${rag.evidence.ensure-source-diversity:true}")
    private boolean ensureSourceDiversity;

    private final EvidenceDeduplicator deduplicator;

    public EvidenceQualityGate(EvidenceDeduplicator deduplicator) {
        this.deduplicator = deduplicator;
    }

    /**
     * Apply quality gate pipeline: dedup -> min score filter -> per-document limit -> source diversity -> max items.
     */
    public List<EvidenceItem> apply(List<EvidenceItem> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return evidence == null ? List.of() : evidence;
        }

        // 1. Deduplicate
        List<EvidenceItem> result = deduplicator.deduplicate(evidence);

        // 2. Remove below min score
        result = result.stream()
                .filter(e -> e.score() >= minScore)
                .collect(Collectors.toCollection(ArrayList::new));

        // 3. Per-document limit (keep top N per document by score)
        result = applyPerDocumentLimit(result);

        // 4. Source diversity: ensure at least 1 KB and 1 INQUIRY if both exist in input
        if (ensureSourceDiversity) {
            result = ensureSourceTypeDiversity(result, evidence);
        }

        // 5. Truncate to max items (keep highest scores)
        result.sort(Comparator.comparingDouble(EvidenceItem::score).reversed());
        if (result.size() > maxItems) {
            result = new ArrayList<>(result.subList(0, maxItems));
        }

        return result;
    }

    /**
     * Per-document limit: keep at most maxPerDocument items per documentId, sorted by score descending.
     */
    List<EvidenceItem> applyPerDocumentLimit(List<EvidenceItem> items) {
        // Group by documentId, keeping insertion order
        Map<String, List<EvidenceItem>> byDocument = new LinkedHashMap<>();
        for (EvidenceItem item : items) {
            byDocument.computeIfAbsent(item.documentId(), k -> new ArrayList<>()).add(item);
        }

        List<EvidenceItem> result = new ArrayList<>();
        for (Map.Entry<String, List<EvidenceItem>> entry : byDocument.entrySet()) {
            List<EvidenceItem> docItems = entry.getValue();
            docItems.sort(Comparator.comparingDouble(EvidenceItem::score).reversed());
            int limit = Math.min(docItems.size(), maxPerDocument);
            result.addAll(docItems.subList(0, limit));
        }
        return result;
    }

    /**
     * Ensure source type diversity: if both INQUIRY and KNOWLEDGE_BASE items existed in the
     * original evidence, ensure at least one of each type is present in the result.
     * Pulls from the deduplicated pool (pre-filtering) if needed.
     */
    List<EvidenceItem> ensureSourceTypeDiversity(List<EvidenceItem> result, List<EvidenceItem> original) {
        boolean originalHasInquiry = original.stream().anyMatch(e -> "INQUIRY".equals(e.sourceType()));
        boolean originalHasKb = original.stream().anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));

        // Only enforce diversity if original had both types
        if (!originalHasInquiry || !originalHasKb) {
            return result;
        }

        boolean resultHasInquiry = result.stream().anyMatch(e -> "INQUIRY".equals(e.sourceType()));
        boolean resultHasKb = result.stream().anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));

        if (!resultHasInquiry) {
            // Find best INQUIRY item from original that meets min score
            original.stream()
                    .filter(e -> "INQUIRY".equals(e.sourceType()) && e.score() >= minScore)
                    .max(Comparator.comparingDouble(EvidenceItem::score))
                    .ifPresent(result::add);
        }

        if (!resultHasKb) {
            // Find best KNOWLEDGE_BASE item from original that meets min score
            original.stream()
                    .filter(e -> "KNOWLEDGE_BASE".equals(e.sourceType()) && e.score() >= minScore)
                    .max(Comparator.comparingDouble(EvidenceItem::score))
                    .ifPresent(result::add);
        }

        return result;
    }

    // Visible for testing
    void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }

    void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    void setMaxPerDocument(int maxPerDocument) {
        this.maxPerDocument = maxPerDocument;
    }

    void setEnsureSourceDiversity(boolean ensureSourceDiversity) {
        this.ensureSourceDiversity = ensureSourceDiversity;
    }
}
