package com.biorad.csrag.interfaces.rest.answer;

public record AnswerDraftRequest(
        String question,
        String tone,
        String channel
) {
}
