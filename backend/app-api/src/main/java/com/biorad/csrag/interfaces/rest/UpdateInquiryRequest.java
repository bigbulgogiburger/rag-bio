package com.biorad.csrag.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record UpdateInquiryRequest(
        @NotBlank(message = "question is required")
        String question
) {
}
