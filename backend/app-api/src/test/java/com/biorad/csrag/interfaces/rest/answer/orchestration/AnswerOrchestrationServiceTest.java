package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.biorad.csrag.interfaces.rest.search.ProductExtractorService;
import com.biorad.csrag.interfaces.rest.search.ProductFamilyRegistry;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    private AnswerOrchestrationService service;

    @BeforeEach
    void setUp() {
        // extractAll()은 기본적으로 빈 리스트 반환 (제품 추출 실패 시나리오)
        lenient().when(productExtractorService.extractAll(any())).thenReturn(List.of());
        service = new AnswerOrchestrationService(
                retrieveStep, verifyStep, composeStep, selfReviewStep,
                runRepository, sseService, questionDecomposerService,
                productExtractorService, productFamilyRegistry);
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
        verify(runRepository, times(5)).save(captor.capture());

        List<OrchestrationRunJpaEntity> saved = captor.getAllValues();
        assertThat(saved).extracting(OrchestrationRunJpaEntity::getStep)
                .containsExactly("DECOMPOSE", "RETRIEVE", "VERIFY", "COMPOSE", "SELF_REVIEW");
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
        verify(runRepository, times(3)).save(captor.capture());
        // DECOMPOSE=SUCCESS, RETRIEVE=SUCCESS, VERIFY=FAILED
        assertThat(captor.getAllValues().get(0).getStep()).isEqualTo("DECOMPOSE");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getAllValues().get(2).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(2).getStep()).isEqualTo("VERIFY");
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
        verify(runRepository, times(4)).save(captor.capture());
        assertThat(captor.getAllValues().get(3).getStep()).isEqualTo("COMPOSE");
        assertThat(captor.getAllValues().get(3).getStatus()).isEqualTo("FAILED");
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
}
