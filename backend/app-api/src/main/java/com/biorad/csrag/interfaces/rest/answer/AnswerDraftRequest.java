package com.biorad.csrag.interfaces.rest.answer;

import jakarta.validation.constraints.NotBlank;

public record AnswerDraftRequest(
        @NotBlank(message = "question is required")
        String question,
        String tone
) {
}
