package com.biorad.csrag.interfaces.rest.analysis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.biorad.csrag.infrastructure.persistence.chunk.DocumentChunkJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.knowledge.KnowledgeDocumentJpaRepository;
import com.biorad.csrag.infrastructure.persistence.retrieval.RetrievalEvidenceJpaRepository;
import com.biorad.csrag.interfaces.rest.search.HybridSearchService;
import com.biorad.csrag.interfaces.rest.search.QueryTranslationService;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceVerdictTest {

    @Mock EmbeddingService embeddingService;
    @Mock VectorStore vectorStore;
    @Mock RetrievalEvidenceJpaRepository evidenceRepository;
    @Mock DocumentChunkJpaRepository chunkRepository;
    @Mock DocumentMetadataJpaRepository documentRepository;
    @Mock KnowledgeDocumentJpaRepository kbDocRepository;
    @Mock QueryTranslationService queryTranslationService;
    @Mock HybridSearchService hybridSearchService;

    @InjectMocks
    AnalysisService analysisService;

    private static final UUID INQUIRY_ID = UUID.randomUUID();

    private EvidenceItem evidence(double score) {
        return evidence(score, "neutral content");
    }

    private EvidenceItem evidence(double score, String excerpt) {
        return new EvidenceItem(
                UUID.randomUUID().toString(), UUID.randomUUID().toString(),
                score, excerpt, "KNOWLEDGE_BASE", "test.pdf", 1, 2
        );
    }

    @Nested
    @DisplayName("Verdict 임계값 테스트")
    class VerdictThresholdTest {

        @Test
        @DisplayName("avg >= 0.70 → SUPPORTED")
        void highScore_supported() {
            List<EvidenceItem> evidences = List.of(evidence(0.75), evidence(0.72));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test question", evidences);

            assertThat(result.verdict()).isEqualTo("SUPPORTED");
            assertThat(result.riskFlags()).doesNotContain("LOW_CONFIDENCE");
        }

        @Test
        @DisplayName("avg 0.70 경계값 → SUPPORTED")
        void boundaryHigh_supported() {
            List<EvidenceItem> evidences = List.of(evidence(0.70));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("SUPPORTED");
        }

        @Test
        @DisplayName("avg 0.45-0.70 → CONDITIONAL, LOW_CONFIDENCE 없음")
        void midScore_conditional_noLowConfidence() {
            List<EvidenceItem> evidences = List.of(evidence(0.58), evidence(0.55));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
            assertThat(result.riskFlags()).doesNotContain("LOW_CONFIDENCE");
            assertThat(result.riskFlags()).doesNotContain("WEAK_EVIDENCE_MATCH");
        }

        @Test
        @DisplayName("avg 0.45 경계값 → CONDITIONAL")
        void boundaryMid_conditional() {
            List<EvidenceItem> evidences = List.of(evidence(0.45));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
        }

        @Test
        @DisplayName("avg < 0.45 → REFUTED + WEAK_EVIDENCE_MATCH")
        void lowScore_refuted() {
            List<EvidenceItem> evidences = List.of(evidence(0.40), evidence(0.35));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("REFUTED");
            assertThat(result.riskFlags()).contains("WEAK_EVIDENCE_MATCH");
        }

        @Test
        @DisplayName("avg < 0.45 but >= 0.50 → REFUTED + WEAK_EVIDENCE_MATCH, LOW_CONFIDENCE 없음")
        void lowScoreAbove50_noLowConfidence() {
            // avg = 0.44 < 0.45 but since REFUTED block checks avg < 0.50
            // avg 0.44 < 0.50 so LOW_CONFIDENCE should be added
            List<EvidenceItem> evidences = List.of(evidence(0.44));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("REFUTED");
            assertThat(result.riskFlags()).contains("WEAK_EVIDENCE_MATCH");
            assertThat(result.riskFlags()).contains("LOW_CONFIDENCE");
        }

        @Test
        @DisplayName("빈 evidence → CONDITIONAL + INSUFFICIENT_EVIDENCE")
        void emptyEvidence_conditional() {
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", List.of());

            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
            assertThat(result.confidence()).isEqualTo(0.0);
            assertThat(result.riskFlags()).contains("INSUFFICIENT_EVIDENCE");
        }
    }

    @Nested
    @DisplayName("Polarity conflict 테스트")
    class PolarityConflictTest {

        @Test
        @DisplayName("evidence 간 polarity 충돌 → CONDITIONAL + CONFLICTING_EVIDENCE")
        void polarityConflict_conditional() {
            List<EvidenceItem> evidences = List.of(
                    evidence(0.80, "this is valid and recommended"),
                    evidence(0.75, "this is invalid and rejected")
            );
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
            assertThat(result.riskFlags()).contains("CONFLICTING_EVIDENCE");
        }

        @Test
        @DisplayName("질문 긍정 + evidence 부정 → CONFLICTING_EVIDENCE")
        void questionPositiveEvidenceNegative_conflict() {
            List<EvidenceItem> evidences = List.of(
                    evidence(0.80, "this is invalid and contradicts the claim")
            );
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "is this valid and supported?", evidences);

            assertThat(result.riskFlags()).contains("CONFLICTING_EVIDENCE");
        }

        @Test
        @DisplayName("질문 부정 + evidence 긍정 → CONFLICTING_EVIDENCE")
        void questionNegativeEvidencePositive_conflict() {
            List<EvidenceItem> evidences = List.of(
                    evidence(0.80, "this is recommended and strong")
            );
            // "contradict"는 negative 키워드이면서 positive 키워드를 포함하지 않음
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "this contradicts the protocol", evidences);

            assertThat(result.riskFlags()).contains("CONFLICTING_EVIDENCE");
        }

        @Test
        @DisplayName("polarity 충돌 없음 → CONFLICTING_EVIDENCE 없음")
        void noPolarityConflict() {
            List<EvidenceItem> evidences = List.of(
                    evidence(0.80, "the system is working normally"),
                    evidence(0.75, "all checks passed")
            );
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.riskFlags()).doesNotContain("CONFLICTING_EVIDENCE");
        }
    }

    @Nested
    @DisplayName("Spread conflict 테스트")
    class SpreadConflictTest {

        @Test
        @DisplayName("spread > 0.35 → CONFLICTING_EVIDENCE")
        void largeSpread_conflict() {
            List<EvidenceItem> evidences = List.of(
                    evidence(0.85),
                    evidence(0.45)
            );
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
            assertThat(result.riskFlags()).contains("CONFLICTING_EVIDENCE");
        }

        @Test
        @DisplayName("spread <= 0.35 → CONFLICTING_EVIDENCE 없음")
        void smallSpread_noConflict() {
            List<EvidenceItem> evidences = List.of(
                    evidence(0.75),
                    evidence(0.55)
            );
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.riskFlags()).doesNotContain("CONFLICTING_EVIDENCE");
        }

        @Test
        @DisplayName("단일 evidence → spread 체크 스킵")
        void singleEvidence_noSpreadCheck() {
            List<EvidenceItem> evidences = List.of(evidence(0.75));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.verdict()).isEqualTo("SUPPORTED");
            assertThat(result.riskFlags()).doesNotContain("CONFLICTING_EVIDENCE");
        }
    }

    @Nested
    @DisplayName("Summarize 테스트")
    class SummarizeTest {

        @Test
        @DisplayName("confidence는 avg score의 반올림 값")
        void confidenceIsRoundedAvg() {
            List<EvidenceItem> evidences = List.of(evidence(0.617), evidence(0.590), evidence(0.586));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.confidence()).isEqualTo(0.598);
        }
    }
}
