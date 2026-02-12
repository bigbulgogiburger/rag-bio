package com.biorad.csrag.inquiry.application.usecase;

public record AskQuestionCommand(
        String question,
        String customerChannel
) {
}
