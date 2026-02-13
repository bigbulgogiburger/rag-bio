package com.biorad.csrag.inquiry.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InquiryDomainTest {

    @Test
    void create_throws_whenQuestionBlank() {
        assertThatThrownBy(() -> Inquiry.create("   ", "email"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("question must not be blank");
    }

    @Test
    void create_normalizesQuestionAndChannel() {
        Inquiry inquiry = Inquiry.create("  제품 사용법 문의  ", "  messenger  ");

        assertThat(inquiry.getQuestion()).isEqualTo("제품 사용법 문의");
        assertThat(inquiry.getCustomerChannel()).isEqualTo("messenger");
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.RECEIVED);
        assertThat(inquiry.getId()).isNotNull();
        assertThat(inquiry.getCreatedAt()).isNotNull();
    }

    @Test
    void stateTransition_marksReviewAndAnswered() {
        Inquiry inquiry = Inquiry.create("문의", "email");

        inquiry.markInReview();
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.IN_REVIEW);

        inquiry.markAnswered();
        assertThat(inquiry.getStatus()).isEqualTo(InquiryStatus.ANSWERED);
    }
}
