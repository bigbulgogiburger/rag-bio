package com.biorad.csrag.inquiry.application.usecase;

public record AskQuestionResult(
        String inquiryId,
        String status,
        String message
) {
}
