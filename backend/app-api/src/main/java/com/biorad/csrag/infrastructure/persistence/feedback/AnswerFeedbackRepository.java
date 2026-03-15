package com.biorad.csrag.infrastructure.persistence.feedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnswerFeedbackRepository extends JpaRepository<AnswerFeedbackEntity, Long> {

    List<AnswerFeedbackEntity> findByInquiryId(Long inquiryId);

    List<AnswerFeedbackEntity> findByInquiryIdAndAnswerId(Long inquiryId, Long answerId);

    long countByRating(String rating);
}
