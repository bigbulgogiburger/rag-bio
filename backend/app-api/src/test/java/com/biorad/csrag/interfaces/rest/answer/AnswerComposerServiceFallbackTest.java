package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.interfaces.rest.answer.orchestration.AnswerOrchestrationService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnswerComposerServiceFallbackTest {

    @Test
    void compose_returnsSafeFallbackDraft_whenOrchestrationFails() {
        AnswerOrchestrationService orchestrationService = mock(AnswerOrchestrationService.class);
        AnswerDraftJpaRepository repository = mock(AnswerDraftJpaRepository.class);

        UUID inquiryId = UUID.randomUUID();
        when(orchestrationService.run(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("forced-failure"));
        when(repository.findTopByInquiryIdOrderByVersionDesc(inquiryId)).thenReturn(Optional.empty());
        when(repository.save(any(AnswerDraftJpaEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AnswerComposerService service = new AnswerComposerService(orchestrationService, repository, java.util.List.of());

        AnswerDraftResponse response = service.compose(inquiryId, "test question", "professional", "email");

        assertThat(response.verdict()).isEqualTo("CONDITIONAL");
        assertThat(response.confidence()).isEqualTo(0.0);
        assertThat(response.riskFlags()).contains("ORCHESTRATION_FALLBACK");
        assertThat(response.citations()).isEmpty();
        assertThat(response.formatWarnings()).contains("FALLBACK_DRAFT_USED");
        assertThat(response.draft()).contains("보수적 안내");
    }
}
