package com.biorad.csrag.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateInquiryRequest(
        @NotBlank(message = "question is required")
        String question,
        String customerChannel
) {
}
