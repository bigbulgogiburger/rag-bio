package com.biorad.csrag.inquiry.domain.model;

import java.time.Instant;
import java.util.Objects;

public class Inquiry {

    private final InquiryId id;
    private String question;
    private final String customerChannel;
    private final String preferredTone;
    private InquiryStatus status;
    private final Instant createdAt;

    private Inquiry(InquiryId id, String question, String customerChannel, String preferredTone, InquiryStatus status, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.question = Objects.requireNonNull(question, "question must not be null");
        this.customerChannel = Objects.requireNonNull(customerChannel, "customerChannel must not be null");
        this.preferredTone = Objects.requireNonNull(preferredTone, "preferredTone must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static Inquiry create(String question, String customerChannel) {
        return create(question, customerChannel, null);
    }

    public static Inquiry create(String question, String customerChannel, String preferredTone) {
        String normalizedQuestion = question == null ? "" : question.trim();
        if (normalizedQuestion.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        String normalizedChannel = (customerChannel == null || customerChannel.isBlank())
                ? "unspecified"
                : customerChannel.trim();
        String normalizedTone = (preferredTone == null || preferredTone.isBlank())
                ? "professional"
                : preferredTone.trim().toLowerCase();
        return new Inquiry(InquiryId.newId(), normalizedQuestion, normalizedChannel, normalizedTone, InquiryStatus.RECEIVED, Instant.now());
    }

    public static Inquiry reconstitute(
            InquiryId id,
            String question,
            String customerChannel,
            InquiryStatus status,
            Instant createdAt
    ) {
        return reconstitute(id, question, customerChannel, "professional", status, createdAt);
    }

    public static Inquiry reconstitute(
            InquiryId id,
            String question,
            String customerChannel,
            String preferredTone,
            InquiryStatus status,
            Instant createdAt
    ) {
        return new Inquiry(id, question, customerChannel,
                preferredTone == null ? "professional" : preferredTone,
                status, createdAt);
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

    public String getPreferredTone() {
        return preferredTone;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateQuestion(String newQuestion) {
        String normalized = newQuestion == null ? "" : newQuestion.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("question must not be blank");
        }
        this.question = normalized;
    }

    public void markInReview() {
        this.status = InquiryStatus.IN_REVIEW;
    }

    public void markAnswered() {
        this.status = InquiryStatus.ANSWERED;
    }
}
