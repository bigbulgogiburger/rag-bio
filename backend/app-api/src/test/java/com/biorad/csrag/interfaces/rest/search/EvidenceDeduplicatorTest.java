package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceDeduplicatorTest {

    private EvidenceDeduplicator deduplicator;

    @BeforeEach
    void setUp() {
        deduplicator = new EvidenceDeduplicator();
    }

    // ── chunkId dedup ──────────────────────────────────────────────

    @Test
    void deduplicate_exactChunkIdDuplicates_keepsHigherScore() {
        String chunkId = UUID.randomUUID().toString();
        EvidenceItem low = evidence(chunkId, "doc1", 0.5, "some content", "INQUIRY");
        EvidenceItem high = evidence(chunkId, "doc1", 0.9, "some content", "INQUIRY");

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(low, high));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0.9);
    }

    @Test
    void deduplicate_differentChunkIds_keepsAll() {
        EvidenceItem a = evidence(UUID.randomUUID().toString(), "doc1", 0.8, "content A", "INQUIRY");
        EvidenceItem b = evidence(UUID.randomUUID().toString(), "doc2", 0.7, "content B completely different", "KNOWLEDGE_BASE");

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(a, b));

        assertThat(result).hasSize(2);
    }

    // ── Jaccard similarity dedup ───────────────────────────────────

    @Test
    void deduplicate_nearDuplicateContent_removesLowerScored() {
        // Nearly identical content (> 0.7 Jaccard similarity)
        String content = "The Bio-Rad QX200 system requires regular calibration of the droplet generator module";
        String nearDup = "The Bio-Rad QX200 system requires regular calibration of the droplet generator unit";

        EvidenceItem high = evidence(UUID.randomUUID().toString(), "doc1", 0.9, content, "INQUIRY");
        EvidenceItem low = evidence(UUID.randomUUID().toString(), "doc2", 0.6, nearDup, "INQUIRY");

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(high, low));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0.9);
    }

    @Test
    void deduplicate_differentContent_keepsBoth() {
        EvidenceItem a = evidence(UUID.randomUUID().toString(), "doc1", 0.9,
                "The QX200 system uses microfluidic channels for droplet generation", "INQUIRY");
        EvidenceItem b = evidence(UUID.randomUUID().toString(), "doc2", 0.6,
                "Calibration protocol requires monthly verification of optical alignment sensors", "INQUIRY");

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(a, b));

        assertThat(result).hasSize(2);
    }

    // ── same-doc same-page dedup ───────────────────────────────────

    @Test
    void deduplicate_sameDocOverlappingPages_keepsHigherScore() {
        String docId = UUID.randomUUID().toString();
        EvidenceItem high = evidence(UUID.randomUUID().toString(), docId, 0.9,
                "Content from page 1-3", "INQUIRY", "file.pdf", 1, 3);
        EvidenceItem low = evidence(UUID.randomUUID().toString(), docId, 0.5,
                "Different content from page 2-4", "INQUIRY", "file.pdf", 2, 4);

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(high, low));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).score()).isEqualTo(0.9);
    }

    @Test
    void deduplicate_sameDocNonOverlappingPages_keepsBoth() {
        String docId = UUID.randomUUID().toString();
        EvidenceItem a = evidence(UUID.randomUUID().toString(), docId, 0.9,
                "The QX200 droplet generator produces uniform water-in-oil emulsions for digital PCR amplification", "INQUIRY", "file.pdf", 1, 3);
        EvidenceItem b = evidence(UUID.randomUUID().toString(), docId, 0.5,
                "Calibration of the fluorescence detector requires monthly alignment verification with reference standards", "INQUIRY", "file.pdf", 5, 7);

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(a, b));

        assertThat(result).hasSize(2);
    }

    @Test
    void deduplicate_differentDocsOverlappingPages_keepsBoth() {
        EvidenceItem a = evidence(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0.9,
                "The QX200 droplet generator produces uniform water-in-oil emulsions for digital PCR amplification", "INQUIRY", "file1.pdf", 1, 3);
        EvidenceItem b = evidence(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 0.5,
                "Calibration of the fluorescence detector requires monthly alignment verification with reference standards", "INQUIRY", "file2.pdf", 2, 4);

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(a, b));

        assertThat(result).hasSize(2);
    }

    // ── edge cases ─────────────────────────────────────────────────

    @Test
    void deduplicate_nullInput_returnsEmptyList() {
        List<EvidenceItem> result = deduplicator.deduplicate(null);

        assertThat(result).isEmpty();
    }

    @Test
    void deduplicate_emptyInput_returnsEmptyList() {
        List<EvidenceItem> result = deduplicator.deduplicate(List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void deduplicate_singleItem_returnsSameItem() {
        EvidenceItem item = evidence(UUID.randomUUID().toString(), "doc1", 0.8, "content", "INQUIRY");

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(item));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(item);
    }

    @Test
    void deduplicate_noDuplicates_returnsAll() {
        EvidenceItem a = evidence(UUID.randomUUID().toString(), "doc1", 0.9,
                "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY");
        EvidenceItem b = evidence(UUID.randomUUID().toString(), "doc2", 0.7,
                "India juliet kilo lima mike november oscar papa", "KNOWLEDGE_BASE");
        EvidenceItem c = evidence(UUID.randomUUID().toString(), "doc3", 0.5,
                "Quebec romeo sierra tango uniform victor whiskey xray", "INQUIRY");

        List<EvidenceItem> result = deduplicator.deduplicate(List.of(a, b, c));

        assertThat(result).hasSize(3);
    }

    // ── Jaccard similarity unit tests ──────────────────────────────

    @Test
    void jaccardSimilarity_identicalStrings_returns1() {
        double sim = deduplicator.jaccardSimilarity("hello world", "hello world");
        assertThat(sim).isEqualTo(1.0);
    }

    @Test
    void jaccardSimilarity_completelyDifferent_returns0() {
        double sim = deduplicator.jaccardSimilarity("alpha bravo charlie", "delta echo foxtrot");
        assertThat(sim).isEqualTo(0.0);
    }

    @Test
    void jaccardSimilarity_nullInput_returns0() {
        assertThat(deduplicator.jaccardSimilarity(null, "hello")).isEqualTo(0.0);
        assertThat(deduplicator.jaccardSimilarity("hello", null)).isEqualTo(0.0);
    }

    @Test
    void jaccardSimilarity_bothEmpty_returns1() {
        assertThat(deduplicator.jaccardSimilarity("", "")).isEqualTo(1.0);
    }

    @Test
    void tokenize_filtersShortTokens() {
        var tokens = deduplicator.tokenize("I am a big dog");
        // "I", "a" are < 2 chars, should be filtered
        assertThat(tokens).contains("am", "big", "dog");
        assertThat(tokens).doesNotContain("I", "a");
    }

    // ── helpers ────────────────────────────────────────────────────

    private EvidenceItem evidence(String chunkId, String docId, double score,
                                   String excerpt, String sourceType) {
        return new EvidenceItem(chunkId, docId, score, excerpt, sourceType, null, null, null, null);
    }

    private EvidenceItem evidence(String chunkId, String docId, double score,
                                   String excerpt, String sourceType,
                                   String fileName, Integer pageStart, Integer pageEnd) {
        return new EvidenceItem(chunkId, docId, score, excerpt, sourceType, fileName, pageStart, pageEnd, null);
    }
}
