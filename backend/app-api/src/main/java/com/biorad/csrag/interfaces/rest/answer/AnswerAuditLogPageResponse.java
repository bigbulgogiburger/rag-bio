package com.biorad.csrag.interfaces.rest.answer;

import java.util.List;

public record AnswerAuditLogPageResponse(
        List<AnswerAuditLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
