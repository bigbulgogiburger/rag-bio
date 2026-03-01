package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.biorad.csrag.interfaces.rest.search.AdaptiveRetrievalAgent;
import com.biorad.csrag.interfaces.rest.search.MultiHopRetriever;
import com.biorad.csrag.interfaces.rest.search.ProductExtractorService;
import com.biorad.csrag.interfaces.rest.search.ProductFamilyRegistry;
import com.biorad.csrag.interfaces.rest.search.RerankingService;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerOrchestrationServiceTest {

    @Mock private RetrieveStep retrieveStep;
    @Mock private VerifyStep verifyStep;
    @Mock private ComposeStep composeStep;
    @Mock private SelfReviewStep selfReviewStep;
    @Mock private OrchestrationRunJpaRepository runRepository;
    @Mock private SseService sseService;
    @Mock private QuestionDecomposerService questionDecomposerService;
    @Mock private ProductExtractorService productExtractorService;
    @Mock private ProductFamilyRegistry productFamilyRegistry;
    @Mock private AdaptiveRetrievalAgent adaptiveRetrievalAgent;
    @Mock private MultiHopRetriever multiHopRetriever;
    @Mock private CriticAgentService criticAgentService;

    private AnswerOrchestrationService service;

    @BeforeEach
    void setUp() {
        // extractAll()은 기본적으로 빈 리스트 반환 (제품 추출 실패 시나리오)
        lenient().when(productExtractorService.extractAll(any())).thenReturn(List.of());
        // AdaptiveRetrieval은 기본적으로 NO_EVIDENCE 반환 (빈 결과 시 fallback)
        lenient().when(adaptiveRetrievalAgent.retrieve(any(), any(), any()))
                .thenReturn(AdaptiveRetrievalAgent.AdaptiveResult.noEvidence(""));
        // MultiHop은 기본적으로 singleHop 반환
        lenient().when(multiHopRetriever.retrieve(any(), any()))
                .thenReturn(MultiHopRetriever.MultiHopResult.singleHop(List.of()));
        // Critic은 기본적으로 통과 반환
        lenient().when(criticAgentService.critique(any(), any(), any()))
                .thenReturn(CriticAgentService.CriticResult.passing(0.95));
        service = new AnswerOrchestrationService(
                retrieveStep, verifyStep, composeStep, selfReviewStep,
                runRepository, sseService, questionDecomposerService,
                productExtractorService, productFamilyRegistry,
                adaptiveRetrievalAgent, multiHopRetriever, criticAgentService);
    }

    /** Helper: stub decomposer to return a single sub-question (default single-question flow). */
    private void stubSingleQuestionDecompose(String question) {
        when(questionDecomposerService.decompose(question)).thenReturn(
                new DecomposedQuestion(question, List.of(new SubQuestion(1, question, null)), null));
    }

    @Test
    void run_executesAllStepsInOrder() {
        UUID inquiryId = UUID.randomUUID();
        String question = "Test question";

        stubSingleQuestionDecompose(question);

        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("chunk-1", "doc-1", 0.9, "test excerpt", "INQUIRY", "test.pdf", 1, 1)
        );
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.85, "reason", List.of(), evidences, null
        );
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult(
                "Draft answer text", List.of()
        );

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any())).thenReturn(composed);
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, question, "professional", "email");

        assertThat(result.analysis()).isEqualTo(analysis);
        assertThat(result.draft()).isEqualTo("Draft answer text");
        assertThat(result.formatWarnings()).isEmpty();
        assertThat(result.perQuestionEvidences()).isNull(); // single question → no per-question evidences

        verify(retrieveStep).execute(eq(inquiryId), eq(question), eq(10));
        verify(verifyStep).execute(eq(inquiryId), eq(question), eq(evidences));
    }

    @Test
    void run_logsSuccessForEachStep() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(List.of());
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(
                new AnalyzeResponse(inquiryId.toString(), "SUPPORTED", 0.8, "", List.of(), List.of(), null));
        when(composeStep.execute(any(), anyString(), any(), any(), any())).thenReturn(
                new ComposeStep.ComposeStepResult("Draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.run(inquiryId, "question", "professional", "email");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(8)).save(captor.capture());

        List<OrchestrationRunJpaEntity> saved = captor.getAllValues();
        assertThat(saved).extracting(OrchestrationRunJpaEntity::getStep)
                .containsExactly("DECOMPOSE", "RETRIEVE", "ADAPTIVE_RETRIEVE",
                        "MULTI_HOP", "VERIFY", "COMPOSE", "CRITIC", "SELF_REVIEW");
        assertThat(saved).allMatch(e -> "SUCCESS".equals(e.getStatus()));
    }

    @Test
    void run_logsFailureWhenStepFails() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        when(retrieveStep.execute(any(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Vector store unavailable"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "question", "professional", "email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Vector store unavailable");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(2)).save(captor.capture());

        // DECOMPOSE succeeded, RETRIEVE failed
        assertThat(captor.getAllValues().get(0).getStep()).isEqualTo("DECOMPOSE");
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getAllValues().get(1).getStep()).isEqualTo("RETRIEVE");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(1).getErrorMessage()).isEqualTo("Vector store unavailable");
    }

    @Test
    void run_verifyStepFails_logsRetrieveSuccessAndVerifyFailure() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        stubSingleQuestionDecompose("q");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList()))
                .thenThrow(new RuntimeException("verify error"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "q", "professional", "email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("verify error");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(5)).save(captor.capture());
        // DECOMPOSE=SUCCESS, RETRIEVE=SUCCESS, ADAPTIVE_RETRIEVE=SUCCESS, MULTI_HOP=SUCCESS, VERIFY=FAILED
        assertThat(captor.getAllValues().get(0).getStep()).isEqualTo("DECOMPOSE");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getAllValues().get(4).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(4).getStep()).isEqualTo("VERIFY");
    }

    @Test
    void run_composeStepFails_logsAllStepStatuses() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null
        );
        stubSingleQuestionDecompose("q");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenThrow(new RuntimeException("compose error"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "q", "professional", "email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("compose error");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(6)).save(captor.capture());
        assertThat(captor.getAllValues().get(5).getStep()).isEqualTo("COMPOSE");
        assertThat(captor.getAllValues().get(5).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void run_sseEmitFails_doesNotBreakPipeline() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null
        );
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult("draft", List.of("warn"));
        stubSingleQuestionDecompose("q");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any())).thenReturn(composed);
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("sse fail")).when(sseService).send(any(), any(), any());

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "q", "professional", "email");

        assertThat(result.draft()).isEqualTo("draft");
        assertThat(result.formatWarnings()).containsExactly("warn");
    }

    @Test
    void run_nullErrorMessage_usesClassName() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("q");

        when(retrieveStep.execute(any(), anyString(), anyInt()))
                .thenThrow(new NullPointerException());
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "q", "professional", "email"))
                .isInstanceOf(NullPointerException.class);

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(2)).save(captor.capture());
        // Second save is the RETRIEVE failure
        assertThat(captor.getAllValues().get(1).getErrorMessage()).isEqualTo("NullPointerException");
    }

    @Test
    void orchestrationResult_recordAccessors() {
        AnalyzeResponse analysis = new AnalyzeResponse("id", "SUPPORTED", 0.9, "r", List.of(), List.of(), null);
        AnswerOrchestrationService.OrchestrationResult result =
                new AnswerOrchestrationService.OrchestrationResult(analysis, "draft", List.of("w1"));

        assertThat(result.analysis()).isEqualTo(analysis);
        assertThat(result.draft()).isEqualTo("draft");
        assertThat(result.formatWarnings()).containsExactly("w1");
        assertThat(result.selfReviewIssues()).isEmpty();
        assertThat(result.perQuestionEvidences()).isNull();
    }

    @Test
    void run_selfReviewFails_usesOriginalDraft() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null
        );
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult("original draft", List.of());
        stubSingleQuestionDecompose("q");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any())).thenReturn(composed);
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenThrow(new RuntimeException("review failed"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "q", "professional", "email");

        assertThat(result.draft()).isEqualTo("original draft");
        assertThat(result.selfReviewIssues()).isEmpty();
    }

    @Test
    void run_selfReviewNotPassed_retriesAndReturnsWarning() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null
        );
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult("draft v1", List.of());
        ComposeStep.ComposeStepResult recomposed = new ComposeStep.ComposeStepResult("draft v2", List.of());
        stubSingleQuestionDecompose("q");

        SelfReviewStep.QualityIssue criticalIssue = new SelfReviewStep.QualityIssue(
                "DUPLICATION", "CRITICAL", "duplicate content", "remove duplicates");
        SelfReviewStep.SelfReviewResult failedReview = new SelfReviewStep.SelfReviewResult(
                false, List.of(criticalIssue), "fix duplicates");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(AnalyzeResponse.class), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(composed);
        when(composeStep.execute(any(AnalyzeResponse.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(recomposed);
        when(selfReviewStep.review(anyString(), anyList(), anyString())).thenReturn(failedReview);
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "q", "professional", "email");

        assertThat(result.draft()).isEqualTo("draft v2");
        assertThat(result.formatWarnings()).contains("SELF_REVIEW_INCOMPLETE");
        assertThat(result.selfReviewIssues()).hasSize(1);
        assertThat(result.selfReviewIssues().getFirst().category()).isEqualTo("DUPLICATION");
    }

    @Test
    void run_multiQuestion_usesPerQuestionRetrieve() {
        UUID inquiryId = UUID.randomUUID();
        String question = "질문 1) naica 사용법 질문 2) 교정 방법";

        // Decompose returns 2 sub-questions
        SubQuestion sq1 = new SubQuestion(1, "naica 사용법", "naica");
        SubQuestion sq2 = new SubQuestion(2, "교정 방법", "naica");
        when(questionDecomposerService.decompose(question)).thenReturn(
                new DecomposedQuestion(question, List.of(sq1, sq2), "naica"));
        when(productExtractorService.extractAll(question)).thenReturn(
                List.of(new ProductExtractorService.ExtractedProduct("naica", "naica", 0.9)));

        // Use DefaultRetrieveStep (concrete) to trigger multi-question path
        // Since our mock is RetrieveStep (interface), the single-question fallback will be used.
        // We test the single-question path here since mock is not instanceof DefaultRetrieveStep.
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("chunk-1", "doc-1", 0.9, "excerpt", "INQUIRY", "test.pdf", 1, 1)
        );
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.85, "reason", List.of(), evidences, null
        );
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult("multi-q draft", List.of());

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any())).thenReturn(composed);
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, question, "professional", "email");

        assertThat(result.draft()).isEqualTo("multi-q draft");
        // When retrieveStep is not DefaultRetrieveStep, falls back to single-question retrieve
        assertThat(result.perQuestionEvidences()).isNull();
        verify(questionDecomposerService).decompose(question);
        verify(productExtractorService).extractAll(question);
    }

    // ── Sprint 4 브랜치 커버 테스트 ───────────────────────────────────────

    @Test
    void run_adaptiveRetrieve_successPath_populatesEvidences() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        // retrieveStep returns empty → triggers ADAPTIVE_RETRIEVE
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(List.of());

        // adaptiveAgent returns SUCCESS with evidences
        RerankingService.RerankResult adaptiveEvidence = new RerankingService.RerankResult(
                UUID.randomUUID(), UUID.randomUUID(), "adaptive content", 0.85, 0.85, "KNOWLEDGE_BASE", "VECTOR");
        AdaptiveRetrievalAgent.AdaptiveResult successResult =
                AdaptiveRetrievalAgent.AdaptiveResult.success(List.of(adaptiveEvidence), 2);
        when(adaptiveRetrievalAgent.retrieve(anyString(), anyString(), any())).thenReturn(successResult);

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(),
                List.of(new EvidenceItem("c1", "d1", 0.85, "adaptive content", "KNOWLEDGE_BASE", null, null, null)), null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("adaptive draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        assertThat(result.draft()).isEqualTo("adaptive draft");
        assertThat(result.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.ADAPTIVE);
        // verifyStep should receive the adaptive evidences
        verify(adaptiveRetrievalAgent).retrieve(anyString(), anyString(), eq(inquiryId));
    }

    @Test
    void run_adaptiveRetrieve_exceptionPath_continuesPipeline() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(List.of());
        when(adaptiveRetrievalAgent.retrieve(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("adaptive failed"));

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "NOT_SUPPORTED", 0.1, "no evidence", List.of(), List.of(), null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("IDK draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Exception should NOT propagate — pipeline continues with empty evidences
        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        assertThat(result.draft()).isEqualTo("IDK draft");
    }

    @Test
    void run_adaptiveRetrieve_lowConfidencePath_populatesEvidences() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(List.of());

        // adaptiveAgent returns LOW_CONFIDENCE (not NO_EVIDENCE) → should populate evidences
        RerankingService.RerankResult lowConfEvidence = new RerankingService.RerankResult(
                UUID.randomUUID(), null, "low conf content", 0.45, 0.45, "INQUIRY", "VECTOR");
        AdaptiveRetrievalAgent.AdaptiveResult lowConfResult =
                AdaptiveRetrievalAgent.AdaptiveResult.lowConfidence(List.of(lowConfEvidence), 0.45);
        when(adaptiveRetrievalAgent.retrieve(anyString(), anyString(), any())).thenReturn(lowConfResult);

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.6, "ok", List.of(),
                List.of(new EvidenceItem("c2", null, 0.45, "low conf content", "INQUIRY", null, null, null)), null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("low conf draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        // LOW_CONFIDENCE is not NO_EVIDENCE → evidences populated, quality = ADAPTIVE
        assertThat(result.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.ADAPTIVE);
    }

    @Test
    void run_multiHop_multiHopResult_mergesEvidences() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        List<EvidenceItem> retrieveEvidences = List.of(
                new EvidenceItem("chunk-retrieve", "doc-1", 0.9, "retrieve content", "INQUIRY", "test.pdf", 1, 1));
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(retrieveEvidences);

        // multiHopRetriever returns multi-hop result with additional evidences
        RerankingService.RerankResult hopEvidence = new RerankingService.RerankResult(
                UUID.randomUUID(), UUID.randomUUID(), "hop content", 0.8, 0.8, "KNOWLEDGE_BASE", "VECTOR");
        MultiHopRetriever.HopRecord hop1 = new MultiHopRetriever.HopRecord(1, "q1", 1, 0.9);
        MultiHopRetriever.HopRecord hop2 = new MultiHopRetriever.HopRecord(2, "q2", 1, 0.8);
        MultiHopRetriever.MultiHopResult multiHopResult = MultiHopRetriever.MultiHopResult.multiHop(
                List.of(hopEvidence), List.of(hop1, hop2));
        when(multiHopRetriever.retrieve(anyString(), any())).thenReturn(multiHopResult);

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.85, "ok", List.of(), retrieveEvidences, null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("multihop draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        assertThat(result.draft()).isEqualTo("multihop draft");
        // verifyStep receives merged evidences (retrieve + hop)
        verify(verifyStep).execute(eq(inquiryId), eq("question"), argThat(list -> list.size() == 2));
    }

    @Test
    void run_multiHop_multiHopResult_emptyHopEvidences_noMerge() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        List<EvidenceItem> retrieveEvidences = List.of(
                new EvidenceItem("chunk-1", "doc-1", 0.9, "content", "INQUIRY", "test.pdf", 1, 1));
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(retrieveEvidences);

        // multiHopRetriever returns multi-hop but with empty evidences
        MultiHopRetriever.MultiHopResult multiHopResult = MultiHopRetriever.MultiHopResult.multiHop(
                List.of(), List.of(new MultiHopRetriever.HopRecord(1, "q1", 0, 0.0)));
        when(multiHopRetriever.retrieve(anyString(), any())).thenReturn(multiHopResult);

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.85, "ok", List.of(), retrieveEvidences, null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        assertThat(result.draft()).isEqualTo("draft");
        // verifyStep receives only the original retrieve evidences (no merge since hop evidences empty)
        verify(verifyStep).execute(eq(inquiryId), eq("question"), argThat(list -> list.size() == 1));
    }

    @Test
    void run_multiHop_exception_continuesPipeline() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("chunk-1", "doc-1", 0.9, "content", "INQUIRY", "test.pdf", 1, 1));
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(multiHopRetriever.retrieve(anyString(), any()))
                .thenThrow(new RuntimeException("multihop failed"));

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.85, "ok", List.of(), evidences, null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Exception should NOT propagate — pipeline continues with existing evidences
        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        assertThat(result.draft()).isEqualTo("draft");
    }

    @Test
    void run_critic_needsRevision_recomposes() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null);
        ComposeStep.ComposeStepResult firstCompose = new ComposeStep.ComposeStepResult("first draft", List.of());
        ComposeStep.ComposeStepResult revisedCompose = new ComposeStep.ComposeStepResult("revised draft", List.of());

        stubSingleQuestionDecompose("question");
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), isNull(), isNull())).thenReturn(firstCompose);
        when(composeStep.execute(any(), anyString(), any(), anyString(), anyString())).thenReturn(revisedCompose);

        // Critic says needs revision
        CriticAgentService.CriticResult criticResult = CriticAgentService.CriticResult.failing(
                0.5, List.of(), List.of("Claim 1 is not supported by evidence"));
        when(criticAgentService.critique(anyString(), anyString(), anyList())).thenReturn(criticResult);

        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        // Revised draft is used after critic feedback
        assertThat(result.draft()).isEqualTo("revised draft");
        assertThat(result.criticResult()).isEqualTo(criticResult);
        // composeStep called twice: initial compose + critic-revision compose
        verify(composeStep, times(2)).execute(any(), anyString(), anyString(), any(), any());
    }

    @Test
    void run_critic_exception_usesOriginalDraft() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null);
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult("original draft", List.of());

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any())).thenReturn(composed);
        when(criticAgentService.critique(anyString(), anyString(), anyList()))
                .thenThrow(new RuntimeException("critic API error"));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Exception should NOT propagate — use original draft
        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "question", "professional", "email");

        assertThat(result.draft()).isEqualTo("original draft");
        assertThat(result.criticResult()).isNull();
    }

    @Test
    void run_productFilter_level0_exactMatch() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("QX700 protocol");

        // Product extracted → filter = forProducts
        when(productExtractorService.extractAll("QX700 protocol")).thenReturn(
                List.of(new ProductExtractorService.ExtractedProduct("QX700", "ddPCR", 0.9)));

        // Level 0 returns evidences (EXACT quality)
        List<EvidenceItem> evidences = List.of(
                new EvidenceItem("chunk-1", "doc-1", 0.95, "QX700 info", "KNOWLEDGE_BASE", "manual.pdf", 1, 3));
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.9, "ok", List.of(), evidences, null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("exact draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result =
                service.run(inquiryId, "QX700 protocol", "professional", "email");

        assertThat(result.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.EXACT);
        assertThat(result.extractedProductFamilies()).contains("ddPCR");
    }

    @Test
    void run_productFilter_level1_categoryExpansion() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("naica ddPCR");

        // Product extracted
        when(productExtractorService.extractAll("naica ddPCR")).thenReturn(
                List.of(new ProductExtractorService.ExtractedProduct("naica", "naica", 0.85)));

        // Level 0 returns empty, Level 1 expansion returns evidences
        List<EvidenceItem> expandedEvidences = List.of(
                new EvidenceItem("chunk-2", "doc-2", 0.8, "naica category", "KNOWLEDGE_BASE", "cat.pdf", 1, 2));
        // First call (Level 0) returns empty, second call (Level 1) returns evidences
        when(retrieveStep.execute(any(), anyString(), anyInt()))
                .thenReturn(List.of())
                .thenReturn(expandedEvidences);

        // productFamilyRegistry.expand returns a wider set
        when(productFamilyRegistry.expand(Set.of("naica"))).thenReturn(Set.of("naica", "digital-PCR"));

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), expandedEvidences, null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("expanded draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result =
                service.run(inquiryId, "naica ddPCR", "professional", "email");

        assertThat(result.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.CATEGORY_EXPANDED);
    }

    @Test
    void run_productFilter_level2_unfilteredFallback() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("unknown product");

        // Product extracted but no expansion
        when(productExtractorService.extractAll("unknown product")).thenReturn(
                List.of(new ProductExtractorService.ExtractedProduct("unknown", "xyz", 0.7)));
        // productFamilyRegistry.expand returns same set (no expansion possible)
        when(productFamilyRegistry.expand(Set.of("xyz"))).thenReturn(Set.of("xyz"));

        // All levels return empty → falls through to Level 2 (unfiltered)
        List<EvidenceItem> unfilteredEvidences = List.of(
                new EvidenceItem("chunk-3", "doc-3", 0.6, "unfiltered content", "KNOWLEDGE_BASE", "gen.pdf", 1, 1));
        when(retrieveStep.execute(any(), anyString(), anyInt()))
                .thenReturn(List.of())   // Level 0
                .thenReturn(List.of())   // Level 1 (same set, skipped)
                .thenReturn(unfilteredEvidences); // Level 2

        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.6, "ok", List.of(), unfilteredEvidences, null);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("unfiltered draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result =
                service.run(inquiryId, "unknown product", "professional", "email");

        assertThat(result.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.UNFILTERED);
    }

    @Test
    void run_withAdditionalInstructions_passesToCompose() {
        UUID inquiryId = UUID.randomUUID();
        stubSingleQuestionDecompose("question");

        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null);
        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any(), any(), any()))
                .thenReturn(new ComposeStep.ComposeStepResult("draft", List.of()));
        when(selfReviewStep.review(anyString(), anyList(), anyString()))
                .thenReturn(new SelfReviewStep.SelfReviewResult(true, List.of(), ""));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.run(inquiryId, "question", "casual", "kakao", "Please be brief", "previous draft");

        verify(composeStep).execute(any(), eq("casual"), eq("kakao"), eq("Please be brief"), eq("previous draft"));
    }

    @Test
    void orchestrationResult_fullConstructor_accessors() {
        AnalyzeResponse analysis = new AnalyzeResponse("id", "SUPPORTED", 0.9, "r", List.of(), List.of(), null);
        CriticAgentService.CriticResult critic = CriticAgentService.CriticResult.passing(0.95);

        AnswerOrchestrationService.OrchestrationResult result =
                new AnswerOrchestrationService.OrchestrationResult(
                        analysis, "draft", List.of("w1"),
                        List.of(), null,
                        AnswerOrchestrationService.RetrievalQuality.EXACT,
                        Set.of("ddPCR"), critic);

        assertThat(result.analysis()).isEqualTo(analysis);
        assertThat(result.draft()).isEqualTo("draft");
        assertThat(result.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.EXACT);
        assertThat(result.extractedProductFamilies()).containsExactly("ddPCR");
        assertThat(result.criticResult()).isEqualTo(critic);
    }

    @Test
    void orchestrationResult_backwardCompatConstructors_haveDefaults() {
        AnalyzeResponse analysis = new AnalyzeResponse("id", "SUPPORTED", 0.9, "r", List.of(), List.of(), null);

        // 3-arg constructor
        var r3 = new AnswerOrchestrationService.OrchestrationResult(analysis, "draft", List.of());
        assertThat(r3.selfReviewIssues()).isEmpty();
        assertThat(r3.perQuestionEvidences()).isNull();
        assertThat(r3.criticResult()).isNull();
        assertThat(r3.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.UNFILTERED);

        // 4-arg constructor
        var r4 = new AnswerOrchestrationService.OrchestrationResult(analysis, "draft", List.of(), List.of());
        assertThat(r4.perQuestionEvidences()).isNull();
        assertThat(r4.criticResult()).isNull();

        // 5-arg constructor
        var r5 = new AnswerOrchestrationService.OrchestrationResult(analysis, "draft", List.of(), List.of(), null);
        assertThat(r5.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.UNFILTERED);
        assertThat(r5.criticResult()).isNull();

        // 7-arg constructor
        var r7 = new AnswerOrchestrationService.OrchestrationResult(
                analysis, "draft", List.of(), List.of(), null,
                AnswerOrchestrationService.RetrievalQuality.ADAPTIVE, Set.of("naica"));
        assertThat(r7.criticResult()).isNull();
        assertThat(r7.retrievalQuality()).isEqualTo(AnswerOrchestrationService.RetrievalQuality.ADAPTIVE);
    }
}
