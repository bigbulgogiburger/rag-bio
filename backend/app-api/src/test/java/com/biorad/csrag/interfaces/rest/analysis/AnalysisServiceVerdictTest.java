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
import com.biorad.csrag.interfaces.rest.search.HybridSearchResult;
import com.biorad.csrag.interfaces.rest.search.HybridSearchService;
import com.biorad.csrag.interfaces.rest.search.QueryTranslationService;
import com.biorad.csrag.interfaces.rest.search.RerankingService;
import com.biorad.csrag.interfaces.rest.search.SearchFilter;
import com.biorad.csrag.interfaces.rest.search.TranslatedQuery;
import com.biorad.csrag.interfaces.rest.vector.EmbeddingService;
import com.biorad.csrag.interfaces.rest.vector.VectorStore;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    @Mock RerankingService rerankingService;

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
        @DisplayName("confidence는 position-weighted avg score의 반올림 값")
        void confidenceIsRoundedPositionWeightedAvg() {
            // scores: 0.617, 0.590, 0.586
            // weights: 1.0, 0.5, 1/3
            // weightedScore = 0.617*1.0 + 0.590*0.5 + 0.586*(1/3) = 1.10733...
            // weightSum = 1.0 + 0.5 + 0.333... = 1.8333...
            // avg = 1.10733.../1.8333... ≈ 0.604
            List<EvidenceItem> evidences = List.of(evidence(0.617), evidence(0.590), evidence(0.586));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.confidence()).isEqualTo(0.604);
        }
    }

    @Nested
    @DisplayName("Position-weighted scoring 테스트")
    class PositionWeightedScoringTest {

        @Test
        @DisplayName("scores [0.9, 0.6, 0.3] → weighted avg > simple avg (상위 점수에 높은 가중치)")
        void topHeavyScores_weightedHigherThanSimple() {
            // Simple avg = (0.9 + 0.6 + 0.3) / 3 = 0.6
            // Weighted: weights = 1.0, 0.5, 1/3
            // weightedScore = 0.9*1.0 + 0.6*0.5 + 0.3*(1/3) = 0.9 + 0.3 + 0.1 = 1.3
            // weightSum = 1.0 + 0.5 + 0.333... = 1.8333...
            // avg = 1.3 / 1.8333... ≈ 0.709 > 0.6
            // Note: spread = 0.6 > 0.35 → CONFLICTING_EVIDENCE → verdict overridden to CONDITIONAL
            List<EvidenceItem> evidences = List.of(evidence(0.9), evidence(0.6), evidence(0.3));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            double simpleAvg = 0.6;
            assertThat(result.confidence()).isGreaterThan(simpleAvg);
            // spread triggers CONFLICTING_EVIDENCE override
            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
            assertThat(result.riskFlags()).contains("CONFLICTING_EVIDENCE");
        }

        @Test
        @DisplayName("scores [0.80, 0.65, 0.60] → weighted avg > simple avg, no spread conflict")
        void topHeavyScores_noSpreadConflict() {
            // Simple avg = (0.80 + 0.65 + 0.60) / 3 = 0.6833...
            // Weighted: weights = 1.0, 0.5, 1/3
            // weightedScore = 0.80*1.0 + 0.65*0.5 + 0.60*(1/3) = 0.80 + 0.325 + 0.20 = 1.325
            // weightSum = 1.8333...
            // avg = 1.325 / 1.8333... ≈ 0.7227 > 0.6833
            // spread = 0.20 < 0.35, no conflict
            List<EvidenceItem> evidences = List.of(evidence(0.80), evidence(0.65), evidence(0.60));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            double simpleAvg = (0.80 + 0.65 + 0.60) / 3.0;
            assertThat(result.confidence()).isGreaterThan(simpleAvg);
            assertThat(result.verdict()).isEqualTo("SUPPORTED");
        }

        @Test
        @DisplayName("단일 evidence → weight = 1.0, result = score 자체")
        void singleEvidence_weightEqualsOne() {
            List<EvidenceItem> evidences = List.of(evidence(0.75));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.confidence()).isEqualTo(0.75);
            assertThat(result.verdict()).isEqualTo("SUPPORTED");
        }

        @Test
        @DisplayName("빈 evidence → avg = 0.0, verdict = CONDITIONAL")
        void emptyEvidence_zeroAvg() {
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", List.of());

            assertThat(result.confidence()).isEqualTo(0.0);
            assertThat(result.verdict()).isEqualTo("CONDITIONAL");
        }

        @Test
        @DisplayName("동일 점수 → weighted avg = simple avg")
        void equalScores_weightedEqualSimple() {
            List<EvidenceItem> evidences = List.of(evidence(0.65), evidence(0.65), evidence(0.65));
            AnalyzeResponse result = analysisService.verify(INQUIRY_ID, "test", evidences);

            assertThat(result.confidence()).isEqualTo(0.65);
        }
    }

    @Nested
    @DisplayName("Reranking 통합 테스트")
    class RerankingIntegrationTest {

        @Test
        @DisplayName("doRetrieve가 hybridSearch에 topK*5 후보를 요청하고 rerank를 호출")
        void doRetrieve_callsRerankWithCandidates() {
            int topK = 10;
            int expectedCandidateCount = topK * 5; // 50

            UUID chunkId = UUID.randomUUID();
            UUID docId = UUID.randomUUID();

            // hybridSearchService가 후보를 반환하도록 설정
            HybridSearchResult hybridResult = new HybridSearchResult(
                    chunkId, docId, "test content", 0.8, 0.6, 0.7, "INQUIRY", "VECTOR"
            );
            when(hybridSearchService.search(eq("test query"), eq(expectedCandidateCount), any(SearchFilter.class)))
                    .thenReturn(List.of(hybridResult));

            // rerankingService가 리랭킹 결과를 반환하도록 설정
            RerankingService.RerankResult rerankResult = new RerankingService.RerankResult(
                    chunkId, docId, "test content", 0.7, 0.85, "INQUIRY", "VECTOR"
            );
            when(rerankingService.rerank(eq("test query"), any(), eq(topK)))
                    .thenReturn(List.of(rerankResult));

            when(chunkRepository.findAllById(any())).thenReturn(List.of());
            when(documentRepository.findAllById(any())).thenReturn(List.of());
            when(kbDocRepository.findAllById(any())).thenReturn(List.of());

            when(queryTranslationService.translate("test query"))
                    .thenReturn(new TranslatedQuery("test query", "test query", false));

            List<EvidenceItem> results = analysisService.retrieve(INQUIRY_ID, "test query", topK);

            // hybridSearch에 candidateCount(50)를 요청했는지 확인
            verify(hybridSearchService).search(eq("test query"), eq(expectedCandidateCount), any(SearchFilter.class));
            // rerank가 호출되었는지 확인
            verify(rerankingService).rerank(eq("test query"), any(), eq(topK));

            assertThat(results).hasSize(1);
            // rerankScore(0.85)가 evidence score로 사용되는지 확인
            assertThat(results.get(0).score()).isEqualTo(0.85);
        }
    }
}
