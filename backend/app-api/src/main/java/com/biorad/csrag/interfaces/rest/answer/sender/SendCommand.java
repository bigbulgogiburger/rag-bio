package com.biorad.csrag.interfaces.rest.answer.sender;

import java.util.UUID;

public record SendCommand(
        UUID inquiryId,
        UUID answerId,
        String channel,
        String actor,
        String draft
) {
}
