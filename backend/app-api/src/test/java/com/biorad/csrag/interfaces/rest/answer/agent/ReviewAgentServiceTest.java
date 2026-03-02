package com.biorad.csrag.interfaces.rest.answer.agent;

import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaRepository;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.prompt.PromptRegistry;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewAgentServiceTest {

    @Mock private AnswerDraftJpaRepository answerDraftRepository;
    @Mock private AiReviewResultJpaRepository aiReviewResultRepository;
    @Mock private InquiryRepository inquiryRepository;

    @Captor private ArgumentCaptor<AiReviewResultJpaEntity> reviewCaptor;
    @Captor private ArgumentCaptor<AnswerDraftJpaEntity> draftCaptor;

    private ReviewAgentService service;

    @BeforeEach
    void setUp() {
        // openaiEnabled=false -> uses mock review
        PromptRegistry promptRegistry = mock(PromptRegistry.class);
        lenient().when(promptRegistry.get(anyString())).thenReturn("Review prompt");
        service = new ReviewAgentService(
                false, "", "https://api.openai.com/v1", "gpt-4",
                new ObjectMapper(), answerDraftRepository, aiReviewResultRepository,
                inquiryRepository, promptRegistry
        );
    }

    @Test
    void review_draftNotFound_throwsNotFoundException() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.review(inquiryId, answerId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void review_mockMode_returnsPassResult() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getStatus()).thenReturn("REVIEWED");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId))
                .thenReturn(Optional.of(draft));
        when(inquiryRepository.findById(any())).thenReturn(Optional.empty());
        when(aiReviewResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiReviewResponse response = service.review(inquiryId, answerId);

        assertThat(response.decision()).isEqualTo("PASS");
        assertThat(response.score()).isEqualTo(85);
        assertThat(response.issues()).isEmpty();
        assertThat(response.reviewedBy()).isEqualTo("ai-review-agent");
        assertThat(response.status()).isEqualTo("REVIEWED");
    }

    @Test
    void review_savesAiReviewEntity() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getStatus()).thenReturn("REVIEWED");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId))
                .thenReturn(Optional.of(draft));
        when(inquiryRepository.findById(any())).thenReturn(Optional.empty());
        when(aiReviewResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.review(inquiryId, answerId);

        verify(aiReviewResultRepository).save(reviewCaptor.capture());
        AiReviewResultJpaEntity saved = reviewCaptor.getValue();
        assertThat(saved.getDecision()).isEqualTo("PASS");
        assertThat(saved.getScore()).isEqualTo(85);
        assertThat(saved.getInquiryId()).isEqualTo(inquiryId);
        assertThat(saved.getAnswerId()).isEqualTo(answerId);
    }

    @Test
    void review_marksDraftAsReviewed() {
        UUID inquiryId = UUID.randomUUID();
        UUID answerId = UUID.randomUUID();
        AnswerDraftJpaEntity draft = mock(AnswerDraftJpaEntity.class);
        when(draft.getStatus()).thenReturn("REVIEWED");
        when(answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId))
                .thenReturn(Optional.of(draft));
        when(inquiryRepository.findById(any())).thenReturn(Optional.empty());
        when(aiReviewResultRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answerDraftRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.review(inquiryId, answerId);

        verify(draft).markAiReviewed(eq("ai-review-agent"), eq(85), eq("PASS"), anyString());
        verify(answerDraftRepository).save(draft);
    }

    @Test
    void reviewResult_recordAccessors() {
        ReviewResult result = new ReviewResult("PASS", 85, java.util.List.of(), null, "Good");
        assertThat(result.decision()).isEqualTo("PASS");
        assertThat(result.score()).isEqualTo(85);
        assertThat(result.issues()).isEmpty();
        assertThat(result.revisedDraft()).isNull();
        assertThat(result.summary()).isEqualTo("Good");
    }

    @Test
    void aiReviewResponse_recordAccessors() {
        AiReviewResponse response = new AiReviewResponse(
                "PASS", 85, java.util.List.of(), null, "summary", "REVIEWED", "reviewer"
        );
        assertThat(response.decision()).isEqualTo("PASS");
        assertThat(response.score()).isEqualTo(85);
        assertThat(response.status()).isEqualTo("REVIEWED");
        assertThat(response.reviewedBy()).isEqualTo("reviewer");
    }
}
