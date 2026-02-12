package com.biorad.csrag.inquiry.domain.model;

import java.util.UUID;

public record InquiryId(UUID value) {

    public static InquiryId newId() {
        return new InquiryId(UUID.randomUUID());
    }
}
