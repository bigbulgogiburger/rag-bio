package com.biorad.csrag.interfaces.rest.analysis;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AnalyzeRequest(
        @NotBlank(message = "question is required")
        String question,

        @Min(1)
        @Max(20)
        Integer topK
) {
}
