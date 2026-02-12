package com.biorad.csrag.inquiry.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Inquiry {

    private final InquiryId id;
    private final String question;
    private final String customerChannel;
    private InquiryStatus status;
    private final Instant createdAt;

    private Inquiry(InquiryId id, String question, String customerChannel, InquiryStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.question = Objects.requireNonNull(question, "question must not be null");
        this.customerChannel = Objects.requireNonNull(customerChannel, "customerChannel must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Inquiry create(String question, String customerChannel) {
        String normalizedQuestion = question == null ? "" : question.trim();
        if (normalizedQuestion.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String normalizedChannel = (customerChannel == null || customerChannel.isBlank())
                ? "unspecified"
                : customerChannel.trim();
        return new Inquiry(InquiryId.newId(), normalizedQuestion, normalizedChannel, InquiryStatus.RECEIVED, Instant.now());
    }

    public static Inquiry reconstitute(
            InquiryId id,
            String question,
            String customerChannel,
            InquiryStatus status,
            Instant createdAt
    ) {
        return new Inquiry(id, question, customerChannel, status, createdAt);
    }

    public InquiryId getId() {
        return id;
    }

    public String getQuestion() {
        return question;
    }

    public String getCustomerChannel() {
        return customerChannel;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markInReview() {
        this.status = InquiryStatus.IN_REVIEW;
    }

    public void markAnswered() {
        this.status = InquiryStatus.ANSWERED;
    }
}
