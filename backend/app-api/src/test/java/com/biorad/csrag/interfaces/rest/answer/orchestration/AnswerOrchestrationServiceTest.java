package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnswerOrchestrationServiceTest {

    @Mock private RetrieveStep retrieveStep;
    @Mock private VerifyStep verifyStep;
    @Mock private ComposeStep composeStep;
    @Mock private OrchestrationRunJpaRepository runRepository;
    @Mock private SseService sseService;

    private AnswerOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new AnswerOrchestrationService(retrieveStep, verifyStep, composeStep, runRepository, sseService);
    }

    @Test
    void run_executesAllStepsInOrder() {
        UUID inquiryId = UUID.randomUUID();
        String question = "Test question";

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
        when(composeStep.execute(any(), anyString(), any())).thenReturn(composed);
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, question, "professional", "email");

        assertThat(result.analysis()).isEqualTo(analysis);
        assertThat(result.draft()).isEqualTo("Draft answer text");
        assertThat(result.formatWarnings()).isEmpty();

        verify(retrieveStep).execute(eq(inquiryId), eq(question), eq(5));
        verify(verifyStep).execute(eq(inquiryId), eq(question), eq(evidences));
        verify(composeStep).execute(eq(analysis), eq("professional"), eq("email"));
    }

    @Test
    void run_logsSuccessForEachStep() {
        UUID inquiryId = UUID.randomUUID();

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(List.of());
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(
                new AnalyzeResponse(inquiryId.toString(), "SUPPORTED", 0.8, "", List.of(), List.of(), null));
        when(composeStep.execute(any(), anyString(), any())).thenReturn(
                new ComposeStep.ComposeStepResult("Draft", List.of()));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.run(inquiryId, "question", "professional", "email");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(3)).save(captor.capture());

        List<OrchestrationRunJpaEntity> saved = captor.getAllValues();
        assertThat(saved).extracting(OrchestrationRunJpaEntity::getStep)
                .containsExactly("RETRIEVE", "VERIFY", "COMPOSE");
        assertThat(saved).allMatch(e -> "SUCCESS".equals(e.getStatus()));
    }

    @Test
    void run_logsFailureWhenStepFails() {
        UUID inquiryId = UUID.randomUUID();

        when(retrieveStep.execute(any(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("Vector store unavailable"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "question", "professional", "email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Vector store unavailable");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository).save(captor.capture());

        OrchestrationRunJpaEntity failedRun = captor.getValue();
        assertThat(failedRun.getStep()).isEqualTo("RETRIEVE");
        assertThat(failedRun.getStatus()).isEqualTo("FAILED");
        assertThat(failedRun.getErrorMessage()).isEqualTo("Vector store unavailable");
    }

    @Test
    void run_verifyStepFails_logsRetrieveSuccessAndVerifyFailure() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList()))
                .thenThrow(new RuntimeException("verify error"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "q", "professional", "email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("verify error");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(captor.getAllValues().get(1).getStep()).isEqualTo("VERIFY");
    }

    @Test
    void run_composeStepFails_logsAllStepStatuses() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null
        );

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any()))
                .thenThrow(new RuntimeException("compose error"));
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "q", "professional", "email"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("compose error");

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues().get(2).getStep()).isEqualTo("COMPOSE");
        assertThat(captor.getAllValues().get(2).getStatus()).isEqualTo("FAILED");
    }

    @Test
    void run_sseEmitFails_doesNotBreakPipeline() {
        UUID inquiryId = UUID.randomUUID();
        List<EvidenceItem> evidences = List.of();
        AnalyzeResponse analysis = new AnalyzeResponse(
                inquiryId.toString(), "SUPPORTED", 0.8, "ok", List.of(), evidences, null
        );
        ComposeStep.ComposeStepResult composed = new ComposeStep.ComposeStepResult("draft", List.of("warn"));

        when(retrieveStep.execute(any(), anyString(), anyInt())).thenReturn(evidences);
        when(verifyStep.execute(any(), anyString(), anyList())).thenReturn(analysis);
        when(composeStep.execute(any(), anyString(), any())).thenReturn(composed);
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("sse fail")).when(sseService).send(any(), any(), any());

        AnswerOrchestrationService.OrchestrationResult result = service.run(inquiryId, "q", "professional", "email");

        assertThat(result.draft()).isEqualTo("draft");
        assertThat(result.formatWarnings()).containsExactly("warn");
    }

    @Test
    void run_nullErrorMessage_usesClassName() {
        UUID inquiryId = UUID.randomUUID();

        when(retrieveStep.execute(any(), anyString(), anyInt()))
                .thenThrow(new NullPointerException());
        when(runRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.run(inquiryId, "q", "professional", "email"))
                .isInstanceOf(NullPointerException.class);

        ArgumentCaptor<OrchestrationRunJpaEntity> captor = ArgumentCaptor.forClass(OrchestrationRunJpaEntity.class);
        verify(runRepository).save(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).isEqualTo("NullPointerException");
    }

    @Test
    void orchestrationResult_recordAccessors() {
        AnalyzeResponse analysis = new AnalyzeResponse("id", "SUPPORTED", 0.9, "r", List.of(), List.of(), null);
        AnswerOrchestrationService.OrchestrationResult result =
                new AnswerOrchestrationService.OrchestrationResult(analysis, "draft", List.of("w1"));

        assertThat(result.analysis()).isEqualTo(analysis);
        assertThat(result.draft()).isEqualTo("draft");
        assertThat(result.formatWarnings()).containsExactly("w1");
    }
}
