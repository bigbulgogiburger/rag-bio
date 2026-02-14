package com.biorad.csrag.interfaces.rest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 문의 목록 조회 응답 DTO
 */
public record InquiryListResponse(
    List<InquiryListItem> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    /**
     * 문의 목록 항목
     */
    public record InquiryListItem(
        UUID inquiryId,
        String question,
        String customerChannel,
        String status,
        int documentCount,
        String latestAnswerStatus,  // nullable
        Instant createdAt
    ) {}
}
