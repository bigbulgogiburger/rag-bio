package com.biorad.csrag.interfaces.rest.feedback;

import com.biorad.csrag.infrastructure.persistence.feedback.AnswerFeedbackEntity;
import com.biorad.csrag.infrastructure.persistence.feedback.AnswerFeedbackRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerFeedbackControllerTest {

    @Mock
    private AnswerFeedbackRepository feedbackRepository;

    private AnswerFeedbackController controller;

    @BeforeEach
    void setUp() {
        controller = new AnswerFeedbackController(feedbackRepository);
    }

    @Test
    void submitFeedback_returns201_whenValid() {
        // given
        AnswerFeedbackController.FeedbackRequest request = new AnswerFeedbackController.FeedbackRequest(
                "HELPFUL", List.of("ACCURATE"), "Good answer"
        );

        AnswerFeedbackEntity saved = new AnswerFeedbackEntity(
                1L, 2L, "HELPFUL", "[\"ACCURATE\"]", "Good answer", "user1"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 100L);

        when(feedbackRepository.save(any(AnswerFeedbackEntity.class))).thenReturn(saved);

        // when
        ResponseEntity<AnswerFeedbackController.FeedbackResponse> response =
                controller.submitFeedback(1L, 2L, request, "user1");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().rating()).isEqualTo("HELPFUL");
        assertThat(response.getBody().id()).isEqualTo(100L);

        ArgumentCaptor<AnswerFeedbackEntity> captor = ArgumentCaptor.forClass(AnswerFeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());
        assertThat(captor.getValue().getInquiryId()).isEqualTo(1L);
        assertThat(captor.getValue().getAnswerId()).isEqualTo(2L);
        assertThat(captor.getValue().getRating()).isEqualTo("HELPFUL");
    }

    @Test
    void submitFeedback_throwsValidation_whenInvalidRating() {
        // given
        AnswerFeedbackController.FeedbackRequest request = new AnswerFeedbackController.FeedbackRequest(
                "INVALID_RATING", null, null
        );

        // when & then
        assertThatThrownBy(() -> controller.submitFeedback(1L, 2L, request, null))
                .isInstanceOf(com.biorad.csrag.common.exception.ValidationException.class)
                .hasMessageContaining("Rating must be one of");
    }

    @Test
    void submitFeedback_throwsValidation_whenNullRating() {
        // given
        AnswerFeedbackController.FeedbackRequest request = new AnswerFeedbackController.FeedbackRequest(
                null, null, null
        );

        // when & then
        assertThatThrownBy(() -> controller.submitFeedback(1L, 2L, request, null))
                .isInstanceOf(com.biorad.csrag.common.exception.ValidationException.class);
    }

    @Test
    void getFeedback_returnsList() {
        // given
        AnswerFeedbackEntity entity1 = new AnswerFeedbackEntity(
                1L, 2L, "HELPFUL", null, "Great", "user1"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(entity1, "id", 10L);

        AnswerFeedbackEntity entity2 = new AnswerFeedbackEntity(
                1L, 2L, "NOT_HELPFUL", "[\"HALLUCINATION\",\"WRONG_PRODUCT\"]", "Bad answer", "user2"
        );
        org.springframework.test.util.ReflectionTestUtils.setField(entity2, "id", 11L);

        when(feedbackRepository.findByInquiryIdAndAnswerId(1L, 2L))
                .thenReturn(List.of(entity1, entity2));

        // when
        ResponseEntity<List<AnswerFeedbackController.FeedbackResponse>> response =
                controller.getFeedback(1L, 2L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody().get(0).rating()).isEqualTo("HELPFUL");
        assertThat(response.getBody().get(0).issues()).isNull();
        assertThat(response.getBody().get(1).rating()).isEqualTo("NOT_HELPFUL");
        assertThat(response.getBody().get(1).issues()).containsExactly("HALLUCINATION", "WRONG_PRODUCT");
    }

    @Test
    void getFeedback_returnsEmptyList_whenNoFeedback() {
        // given
        when(feedbackRepository.findByInquiryIdAndAnswerId(99L, 99L))
                .thenReturn(List.of());

        // when
        ResponseEntity<List<AnswerFeedbackController.FeedbackResponse>> response =
                controller.getFeedback(99L, 99L);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEmpty();
    }

    @Test
    void submitFeedback_handlesNullUserId() {
        // given
        AnswerFeedbackController.FeedbackRequest request = new AnswerFeedbackController.FeedbackRequest(
                "PARTIALLY_HELPFUL", null, "Okay"
        );

        AnswerFeedbackEntity saved = new AnswerFeedbackEntity(
                5L, 10L, "PARTIALLY_HELPFUL", null, "Okay", null
        );
        org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 200L);

        when(feedbackRepository.save(any(AnswerFeedbackEntity.class))).thenReturn(saved);

        // when
        ResponseEntity<AnswerFeedbackController.FeedbackResponse> response =
                controller.submitFeedback(5L, 10L, request, null);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().submittedBy()).isNull();
    }
}
