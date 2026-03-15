package com.biorad.csrag.interfaces.rest.search;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceQualityGateTest {

    private EvidenceQualityGate gate;

    @BeforeEach
    void setUp() {
        EvidenceDeduplicator deduplicator = new EvidenceDeduplicator();
        gate = new EvidenceQualityGate(deduplicator);
        // Set defaults matching @Value defaults
        gate.setMaxItems(8);
        gate.setMinScore(0.30);
        gate.setMaxPerDocument(3);
        gate.setEnsureSourceDiversity(true);
    }

    // ── min score filtering ────────────────────────────────────────

    @Test
    void apply_filtersBelowMinScore() {
        gate.setMinScore(0.5);

        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.8, "high score", "INQUIRY"),
                evidence("doc2", 0.3, "low score", "INQUIRY"),
                evidence("doc3", 0.6, "medium score", "INQUIRY")
        );

        List<EvidenceItem> result = gate.apply(input);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> e.score() >= 0.5);
    }

    @Test
    void apply_allBelowMinScore_returnsEmpty() {
        gate.setMinScore(0.9);

        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.5, "content A", "INQUIRY"),
                evidence("doc2", 0.3, "content B different words entirely", "INQUIRY")
        );

        List<EvidenceItem> result = gate.apply(input);

        assertThat(result).isEmpty();
    }

    // ── max items limit ────────────────────────────────────────────

    @Test
    void apply_truncatesToMaxItems() {
        gate.setMaxItems(3);
        gate.setMinScore(0.0);
        gate.setMaxPerDocument(10);

        // Each item must have completely distinct content to survive Jaccard dedup
        String[] distinctContents = {
                "Alpha bravo charlie delta echo foxtrot golf hotel india juliet",
                "Kilo lima mike november oscar papa quebec romeo sierra tango",
                "Uniform victor whiskey xray yankee zulu neptune saturn jupiter mars",
                "Hydrogen helium lithium beryllium boron carbon nitrogen oxygen fluorine neon",
                "Mercury venus earth pluto asteroid comet galaxy nebula pulsar quasar",
                "Photosynthesis mitochondria ribosome chromosome deoxyribonucleic enzyme catalyst polymer",
                "Telescope microscope spectrometer oscilloscope barometer thermometer hygrometer anemometer",
                "Fibonacci algorithm recursion iteration polymorphism encapsulation abstraction inheritance",
                "Capacitor resistor inductor transistor diode semiconductor integrated circuit amplifier",
                "Renaissance baroque classical romantic impressionism expressionism cubism surrealism dadaism"
        };

        List<EvidenceItem> input = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            input.add(evidence(UUID.randomUUID().toString(), 0.9 - i * 0.05,
                    distinctContents[i], "INQUIRY"));
        }

        List<EvidenceItem> result = gate.apply(input);

        assertThat(result).hasSize(3);
        // Should keep highest scores
        assertThat(result.get(0).score()).isGreaterThanOrEqualTo(result.get(1).score());
        assertThat(result.get(1).score()).isGreaterThanOrEqualTo(result.get(2).score());
    }

    // ── per-document limit ─────────────────────────────────────────

    @Test
    void apply_limitsPerDocument() {
        gate.setMaxPerDocument(2);
        gate.setMinScore(0.0);
        gate.setMaxItems(20);

        String docId = UUID.randomUUID().toString();
        List<EvidenceItem> input = List.of(
                evidence(docId, 0.9, "Alpha bravo charlie delta echo foxtrot golf", "INQUIRY"),
                evidence(docId, 0.8, "Hotel india juliet kilo lima mike november", "INQUIRY"),
                evidence(docId, 0.7, "Oscar papa quebec romeo sierra tango uniform", "INQUIRY"),
                evidence(UUID.randomUUID().toString(), 0.6, "Victor whiskey xray yankee zulu alpha bravo", "INQUIRY")
        );

        List<EvidenceItem> result = gate.apply(input);

        // 2 from same doc + 1 from other doc = 3
        long sameDocCount = result.stream()
                .filter(e -> e.documentId().equals(docId))
                .count();
        assertThat(sameDocCount).isEqualTo(2);
        assertThat(result).hasSize(3);
    }

    // ── source diversity ───────────────────────────────────────────

    @Test
    void apply_ensuresSourceDiversity_addsKbWhenMissing() {
        gate.setMinScore(0.5);
        gate.setMaxItems(10);
        gate.setEnsureSourceDiversity(true);

        // High-scored INQUIRY items, one low-scored KB item
        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.9, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                evidence("doc2", 0.8, "India juliet kilo lima mike november oscar papa", "INQUIRY"),
                evidence("doc3", 0.7, "Quebec romeo sierra tango uniform victor whiskey xray", "INQUIRY"),
                evidence("doc4", 0.55, "Yankee zulu alpha bravo charlie delta echo foxtrot", "KNOWLEDGE_BASE")
        );

        List<EvidenceItem> result = gate.apply(input);

        boolean hasKb = result.stream().anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
        assertThat(hasKb).isTrue();
    }

    @Test
    void apply_ensuresSourceDiversity_addsInquiryWhenMissing() {
        gate.setMinScore(0.5);
        gate.setMaxItems(10);
        gate.setMaxPerDocument(1);
        gate.setEnsureSourceDiversity(true);

        // KB items dominate, but per-doc limit might remove INQUIRY
        // We want at least one INQUIRY if original had one
        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.55, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                evidence("doc2", 0.9, "India juliet kilo lima mike november oscar papa", "KNOWLEDGE_BASE"),
                evidence("doc3", 0.85, "Quebec romeo sierra tango uniform victor whiskey xray", "KNOWLEDGE_BASE")
        );

        List<EvidenceItem> result = gate.apply(input);

        boolean hasInquiry = result.stream().anyMatch(e -> "INQUIRY".equals(e.sourceType()));
        assertThat(hasInquiry).isTrue();
    }

    @Test
    void apply_diversityDisabled_doesNotForce() {
        gate.setMinScore(0.5);
        gate.setMaxItems(2);
        gate.setEnsureSourceDiversity(false);

        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.9, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                evidence("doc2", 0.8, "India juliet kilo lima mike november oscar papa", "INQUIRY"),
                evidence("doc3", 0.3, "Quebec romeo sierra tango uniform victor whiskey xray", "KNOWLEDGE_BASE")
        );

        List<EvidenceItem> result = gate.apply(input);

        // KB item is below min score, diversity is off, so no KB
        boolean hasKb = result.stream().anyMatch(e -> "KNOWLEDGE_BASE".equals(e.sourceType()));
        assertThat(hasKb).isFalse();
    }

    @Test
    void apply_onlyOneSourceType_doesNotForceOther() {
        gate.setMinScore(0.0);
        gate.setEnsureSourceDiversity(true);

        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.9, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                evidence("doc2", 0.8, "India juliet kilo lima mike november oscar papa", "INQUIRY")
        );

        List<EvidenceItem> result = gate.apply(input);

        // Only INQUIRY in input, should not crash or force KB
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(e -> "INQUIRY".equals(e.sourceType()));
    }

    // ── combined pipeline ──────────────────────────────────────────

    @Test
    void apply_combinedPipeline_dedupAndFilterAndLimit() {
        gate.setMaxItems(3);
        gate.setMinScore(0.4);
        gate.setMaxPerDocument(2);
        gate.setEnsureSourceDiversity(false);

        String chunkId = UUID.randomUUID().toString();
        String docId = UUID.randomUUID().toString();

        List<EvidenceItem> input = List.of(
                // Duplicate chunkId (should keep 0.9)
                evidence(chunkId, docId, 0.9, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                evidence(chunkId, docId, 0.5, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                // Below min score (should be filtered)
                evidence(UUID.randomUUID().toString(), "doc2", 0.2,
                        "India juliet kilo lima mike november oscar papa", "INQUIRY"),
                // Valid items
                evidence(UUID.randomUUID().toString(), "doc3", 0.7,
                        "Quebec romeo sierra tango uniform victor whiskey xray", "KNOWLEDGE_BASE"),
                evidence(UUID.randomUUID().toString(), "doc4", 0.6,
                        "Yankee zulu one two three four five six", "INQUIRY")
        );

        List<EvidenceItem> result = gate.apply(input);

        // chunkId dedup: 5 → 4, min score filter: 4 → 3, max items: 3
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(e -> e.score() >= 0.4);
        // Sorted by score descending
        assertThat(result.get(0).score()).isGreaterThanOrEqualTo(result.get(1).score());
    }

    // ── edge cases ─────────────────────────────────────────────────

    @Test
    void apply_nullInput_returnsEmptyList() {
        List<EvidenceItem> result = gate.apply(null);
        assertThat(result).isEmpty();
    }

    @Test
    void apply_emptyInput_returnsEmptyList() {
        List<EvidenceItem> result = gate.apply(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void apply_singleItem_aboveMinScore_returnsIt() {
        EvidenceItem item = evidence("doc1", 0.8, "content here", "INQUIRY");
        List<EvidenceItem> result = gate.apply(List.of(item));
        assertThat(result).hasSize(1);
    }

    @Test
    void apply_resultsSortedByScoreDescending() {
        gate.setMinScore(0.0);
        gate.setMaxItems(10);

        List<EvidenceItem> input = List.of(
                evidence("doc1", 0.3, "Alpha bravo charlie delta echo foxtrot golf hotel", "INQUIRY"),
                evidence("doc2", 0.9, "India juliet kilo lima mike november oscar papa", "INQUIRY"),
                evidence("doc3", 0.6, "Quebec romeo sierra tango uniform victor whiskey xray", "INQUIRY")
        );

        List<EvidenceItem> result = gate.apply(input);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).score()).isEqualTo(0.9);
        assertThat(result.get(1).score()).isEqualTo(0.6);
        assertThat(result.get(2).score()).isEqualTo(0.3);
    }

    // ── helpers ────────────────────────────────────────────────────

    private EvidenceItem evidence(String docId, double score, String excerpt, String sourceType) {
        return new EvidenceItem(
                UUID.randomUUID().toString(), docId, score, excerpt,
                sourceType, null, null, null, null
        );
    }

    private EvidenceItem evidence(String chunkId, String docId, double score,
                                   String excerpt, String sourceType) {
        return new EvidenceItem(chunkId, docId, score, excerpt, sourceType, null, null, null, null);
    }
}
