package com.biorad.csrag.inquiry.application.usecase;

public record AskQuestionCommand(
        String question,
        String customerChannel,
        String preferredTone
) {
    public AskQuestionCommand(String question, String customerChannel) {
        this(question, customerChannel, null);
    }
}
