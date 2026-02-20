package com.biorad.csrag.interfaces.rest.answer;

public record EmailPreviewResponse(
        String html,
        String subject
) {
}
