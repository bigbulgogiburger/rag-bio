package com.biorad.csrag.interfaces.rest;

import java.time.Instant;

public record InquiryDetailResponse(
        String inquiryId,
        String question,
        String customerChannel,
        String preferredTone,
        String status,
        Instant createdAt
) {
}
