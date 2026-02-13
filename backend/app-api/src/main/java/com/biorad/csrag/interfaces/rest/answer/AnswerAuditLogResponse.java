package com.biorad.csrag.interfaces.rest.answer;

import java.time.Instant;

public record AnswerAuditLogResponse(
        String answerId,
        int version,
        String status,
        String reviewedBy,
        String reviewComment,
        String approvedBy,
        String approveComment,
        String sentBy,
        String sendChannel,
        String sendMessageId,
        Instant createdAt,
        Instant updatedAt
) {
}
