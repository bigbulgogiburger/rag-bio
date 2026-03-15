package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TASK 2-4 (Compose Prompt Efficiency) and TASK 2-5 (Critic Efficiency).
 * Covers compact evidence formatting, token budget enforcement, source type abbreviation,
 * score ordering, and edge cases.
 */
class ComposePromptEfficiencyTest {

    // ── Compose compact format tests ──────────────────────────────────────

    @Nested
    class ComposeFormatTests {

        @Test
        void compactFormat_matchesExpectedPattern() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.92, "The thermal cycling protocol requires...",
                    "KNOWLEDGE_BASE", "CFX96_Manual_v3.2.pdf", 45, 47);

            String formatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            assertThat(formatted).isEqualTo(
                    "[1|CFX96_Manual_v3.2.pdf:45-47|KB|0.92] The thermal cycling protocol requires...");
        }

        @Test
        void compactFormat_sameStartAndEndPage_showsSinglePage() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.85, "Single page content",
                    "INQUIRY", "report.pdf", 10, 10);

            String formatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            assertThat(formatted).isEqualTo("[1|report.pdf:10|INQ|0.85] Single page content");
        }

        @Test
        void compactFormat_nullPages_omitsPageRange() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.75, "No page info",
                    "KNOWLEDGE_BASE", "guide.docx", null, null);

            String formatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            assertThat(formatted).isEqualTo("[1|guide.docx|KB|0.75] No page info");
        }

        @Test
        void compactFormat_nullFileName_showsUnknown() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.60, "Some excerpt",
                    "INQUIRY", null, null, null);

            String formatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            assertThat(formatted).isEqualTo("[1|unknown|INQ|0.60] Some excerpt");
        }

        @Test
        void compactFormat_nullExcerpt_showsEmpty() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.50, null,
                    "INQUIRY", "file.pdf", 1, 1);

            String formatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            assertThat(formatted).isEqualTo("[1|file.pdf:1|INQ|0.50] ");
        }

        @Test
        void compactFormat_scoreRoundedToTwoDecimals() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.9876, "Content",
                    "INQUIRY", "file.pdf", 1, 1);

            String formatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            assertThat(formatted).contains("|0.99]");
        }
    }

    // ── Source type abbreviation tests ─────────────────────────────────────

    @Nested
    class SourceTypeAbbreviationTests {

        @Test
        void knowledgeBase_abbreviatedToKB() {
            assertThat(OpenAiComposeStep.abbreviateSourceType("KNOWLEDGE_BASE")).isEqualTo("KB");
        }

        @Test
        void inquiry_abbreviatedToINQ() {
            assertThat(OpenAiComposeStep.abbreviateSourceType("INQUIRY")).isEqualTo("INQ");
        }

        @Test
        void nullSourceType_defaultsToINQ() {
            assertThat(OpenAiComposeStep.abbreviateSourceType(null)).isEqualTo("INQ");
        }

        @Test
        void unknownSourceType_defaultsToINQ() {
            assertThat(OpenAiComposeStep.abbreviateSourceType("SOMETHING_ELSE")).isEqualTo("INQ");
        }
    }

    // ── Token estimation tests ────────────────────────────────────────────

    @Nested
    class TokenEstimationTests {

        @Test
        void englishText_dividesBy4() {
            // 40 ASCII chars -> 40/4 = 10 tokens
            String english = "The thermal cycling protocol requires a";
            assertThat(OpenAiComposeStep.estimateTokens(english)).isEqualTo(10);
        }

        @Test
        void koreanText_dividesBy3() {
            // Predominantly Korean text (> 1/3 non-ASCII)
            String korean = "열 순환 프로토콜이 필요합니다 확인하세요";
            int tokens = OpenAiComposeStep.estimateTokens(korean);
            // Expecting chars/3 (rounded up)
            int expected = (int) Math.ceil((double) korean.length() / 3);
            assertThat(tokens).isEqualTo(expected);
        }

        @Test
        void nullText_returnsZero() {
            assertThat(OpenAiComposeStep.estimateTokens(null)).isEqualTo(0);
        }

        @Test
        void emptyText_returnsZero() {
            assertThat(OpenAiComposeStep.estimateTokens("")).isEqualTo(0);
        }
    }

    // ── Token budget enforcement tests ────────────────────────────────────

    @Nested
    class TokenBudgetTests {

        private OpenAiComposeStep composeStep;

        @BeforeEach
        void setUp() {
            // Create with a very small token budget to force trimming
            composeStep = new OpenAiComposeStep(null, "test-model", 50,
                    null, null, null);
        }

        @Test
        void applyTokenBudget_sortsEvidenceByScoreDescending() {
            List<EvidenceItem> evidences = List.of(
                    new EvidenceItem("c1", "d1", 0.70, "Low", "INQUIRY", "a.pdf", 1, 1),
                    new EvidenceItem("c2", "d2", 0.95, "High", "INQUIRY", "b.pdf", 2, 2),
                    new EvidenceItem("c3", "d3", 0.80, "Med", "INQUIRY", "c.pdf", 3, 3)
            );

            List<EvidenceItem> result = composeStep.applyTokenBudget(evidences);

            // Regardless of budget, first item should be the highest scored
            assertThat(result).isNotEmpty();
            assertThat(result.get(0).score()).isEqualTo(0.95);
            if (result.size() > 1) {
                assertThat(result.get(1).score()).isEqualTo(0.80);
            }
        }

        @Test
        void applyTokenBudget_trimsWhenOverBudget() {
            // Create many large evidences that exceed the 50-token budget
            List<EvidenceItem> evidences = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String longExcerpt = "A".repeat(300); // ~75 tokens each in English
                evidences.add(new EvidenceItem(
                        "c" + i, "d" + i, 0.90 - (i * 0.05), longExcerpt,
                        "INQUIRY", "file" + i + ".pdf", i, i));
            }

            List<EvidenceItem> result = composeStep.applyTokenBudget(evidences);

            // Should trim: 10 items with ~75 tokens each cannot all fit in 50 token budget
            // At least the first item should always be included
            assertThat(result.size()).isGreaterThanOrEqualTo(1);
            assertThat(result.size()).isLessThan(10);
        }

        @Test
        void applyTokenBudget_firstItemAlwaysIncluded() {
            // Even if a single evidence exceeds budget, it should still be included
            OpenAiComposeStep tinyBudget = new OpenAiComposeStep(null, "test-model", 1,
                    null, null, null);

            List<EvidenceItem> evidences = List.of(
                    new EvidenceItem("c1", "d1", 0.95, "A".repeat(500),
                            "INQUIRY", "big.pdf", 1, 10));

            List<EvidenceItem> result = tinyBudget.applyTokenBudget(evidences);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).score()).isEqualTo(0.95);
        }

        @Test
        void applyTokenBudget_emptyList_returnsEmpty() {
            assertThat(composeStep.applyTokenBudget(List.of())).isEmpty();
        }

        @Test
        void applyTokenBudget_nullList_returnsEmpty() {
            assertThat(composeStep.applyTokenBudget(null)).isEmpty();
        }

        @Test
        void applyTokenBudget_singleEvidence_alwaysIncluded() {
            List<EvidenceItem> evidences = List.of(
                    new EvidenceItem("c1", "d1", 0.90, "Short", "INQUIRY", "a.pdf", 1, 1));

            List<EvidenceItem> result = composeStep.applyTokenBudget(evidences);

            assertThat(result).hasSize(1);
        }

        @Test
        void applyTokenBudget_allWithinBudget_returnsAll() {
            OpenAiComposeStep largeBudget = new OpenAiComposeStep(null, "test-model", 100000,
                    null, null, null);

            List<EvidenceItem> evidences = List.of(
                    new EvidenceItem("c1", "d1", 0.95, "Short 1", "INQUIRY", "a.pdf", 1, 1),
                    new EvidenceItem("c2", "d2", 0.85, "Short 2", "KB", "b.pdf", 2, 2),
                    new EvidenceItem("c3", "d3", 0.75, "Short 3", "INQUIRY", "c.pdf", 3, 3)
            );

            List<EvidenceItem> result = largeBudget.applyTokenBudget(evidences);

            assertThat(result).hasSize(3);
        }

        @Test
        void getEvidenceTokenBudget_returnsConfiguredValue() {
            assertThat(composeStep.getEvidenceTokenBudget()).isEqualTo(50);
        }
    }

    // ── Critic compact format tests ───────────────────────────────────────

    @Nested
    class CriticFormatTests {

        @Test
        void criticCompactFormat_matchesExpectedPattern() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.88, "Evidence content here",
                    "KNOWLEDGE_BASE", "manual.pdf", 10, 12);

            String formatted = OpenAiCriticAgentService.formatEvidenceCompact(1, ev);

            assertThat(formatted).isEqualTo(
                    "[1|manual.pdf:10-12|KB|0.88] Evidence content here");
        }

        @Test
        void criticCompactFormat_truncatesLongExcerpt() {
            String longExcerpt = "A".repeat(500);
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.90, longExcerpt,
                    "INQUIRY", "doc.pdf", 1, 1);

            String formatted = OpenAiCriticAgentService.formatEvidenceCompact(1, ev);

            // Should contain 300 chars + "..."
            assertThat(formatted).contains("A".repeat(300) + "...");
            // Total excerpt in output should not exceed 303 chars (300 + "...")
            String excerptPart = formatted.substring(formatted.indexOf("] ") + 2);
            assertThat(excerptPart).hasSize(303);
        }

        @Test
        void criticCompactFormat_shortExcerptNotTruncated() {
            String shortExcerpt = "Short text";
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.90, shortExcerpt,
                    "INQUIRY", "doc.pdf", 1, 1);

            String formatted = OpenAiCriticAgentService.formatEvidenceCompact(1, ev);

            assertThat(formatted).endsWith(shortExcerpt);
            assertThat(formatted).doesNotContain("...");
        }

        @Test
        void criticSourceType_sameAbbreviationsAsCompose() {
            assertThat(OpenAiCriticAgentService.abbreviateSourceType("KNOWLEDGE_BASE")).isEqualTo("KB");
            assertThat(OpenAiCriticAgentService.abbreviateSourceType("INQUIRY")).isEqualTo("INQ");
            assertThat(OpenAiCriticAgentService.abbreviateSourceType(null)).isEqualTo("INQ");
        }
    }

    // ── Critic skip optimization tests ────────────────────────────────────

    @Nested
    class CriticSkipTests {

        @Test
        void criticSkip_resetSkipState_allowsNextCall() {
            // Manually test the resetSkipState behavior via the public API contract.
            // The actual Critic LLM call is tested in OpenAiCriticAgentServiceTest.
            // Here we only verify that the skip mechanism state management is correct.
            OpenAiCriticAgentService service = new OpenAiCriticAgentService(
                    null, "test-model", null, null, null);

            // After reset, lastFaithfulnessScore is null, so critique should NOT skip.
            // Since RestClient is null, it will throw and return the default passing result.
            service.resetSkipState();
            CriticAgentService.CriticResult result = service.critique("draft", "q", List.of());

            // Default passing result has score 1.0 and no revision
            assertThat(result.faithfulnessScore()).isEqualTo(1.0);
            assertThat(result.needsRevision()).isFalse();
        }
    }

    // ── Integration: Compose format consistency ───────────────────────────

    @Nested
    class FormatConsistencyTests {

        @Test
        void composeAndCritic_produceConsistentHeaderFormat() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.88, "Excerpt text",
                    "KNOWLEDGE_BASE", "manual.pdf", 10, 12);

            String composeFormatted = OpenAiComposeStep.formatEvidenceCompact(1, ev);
            String criticFormatted = OpenAiCriticAgentService.formatEvidenceCompact(1, ev);

            // Both should have the same header format (Critic may truncate excerpt, but here it's short)
            assertThat(composeFormatted).startsWith("[1|manual.pdf:10-12|KB|0.88]");
            assertThat(criticFormatted).startsWith("[1|manual.pdf:10-12|KB|0.88]");

            // For short excerpts, both should produce identical output
            assertThat(composeFormatted).isEqualTo(criticFormatted);
        }

        @Test
        void compactFormat_significantlyMoreConciseThanVerbose() {
            EvidenceItem ev = new EvidenceItem(
                    "c1", "d1", 0.92, "The thermal cycling protocol requires...",
                    "KNOWLEDGE_BASE", "CFX96_Manual_v3.2.pdf", 45, 47);

            String compact = OpenAiComposeStep.formatEvidenceCompact(1, ev);

            // Old verbose format would be:
            // "[근거 1] 파일: CFX96_Manual_v3.2.pdf p.45-47\n소스: KNOWLEDGE_BASE\n관련도: 0.92\n내용: The thermal...\n\n"
            // Compact should be noticeably shorter
            String verboseEstimate = "[근거 1] 파일: CFX96_Manual_v3.2.pdf p.45-47\n소스: KNOWLEDGE_BASE\n관련도: 0.92\n내용: The thermal cycling protocol requires...\n\n";

            assertThat(compact.length()).isLessThan(verboseEstimate.length());
        }
    }
}
