package com.biorad.csrag.testutil;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.model.InquiryStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Centralized test data factory for creating domain entities and JPA entities.
 * Provides sensible defaults while allowing override for specific test scenarios.
 */
public final class TestFixtures {

    private TestFixtures() {}

    // ===== Inquiry Domain =====

    public static Inquiry defaultInquiry() {
        return Inquiry.create("테스트 질문입니다", "email");
    }

    public static Inquiry inquiryWithTone(String tone) {
        return Inquiry.create("테스트 질문입니다", "email", tone);
    }

    public static Inquiry inquiryWithChannel(String channel) {
        return Inquiry.create("테스트 질문입니다", channel);
    }

    public static Inquiry reconstitutedInquiry(UUID id, InquiryStatus status) {
        return Inquiry.reconstitute(
                new InquiryId(id),
                "재구성된 질문",
                "portal",
                "professional",
                status,
                Instant.parse("2025-01-15T10:00:00Z")
        );
    }

    // ===== Document JPA Entity =====

    public static DocumentMetadataJpaEntity defaultDocument(UUID inquiryId) {
        return new DocumentMetadataJpaEntity(
                UUID.randomUUID(),
                inquiryId,
                "test-manual.pdf",
                "application/pdf",
                1024L,
                "/uploads/test-manual.pdf",
                "UPLOADED",
                null,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now()
        );
    }

    public static DocumentMetadataJpaEntity indexedDocument(UUID inquiryId) {
        return new DocumentMetadataJpaEntity(
                UUID.randomUUID(),
                inquiryId,
                "indexed-doc.pdf",
                "application/pdf",
                2048L,
                "/uploads/indexed-doc.pdf",
                "INDEXED",
                "This is extracted text content for testing.",
                null,
                null,
                5,
                5,
                Instant.now(),
                Instant.now()
        );
    }

    // ===== Answer Draft JPA Entity =====

    public static AnswerDraftJpaEntity defaultDraft(UUID inquiryId) {
        return new AnswerDraftJpaEntity(
                UUID.randomUUID(),
                inquiryId,
                1,
                "SUPPORTED",
                0.85,
                "professional",
                "email",
                "DRAFT",
                "This is a test draft answer with citations.",
                "[\"Manual p.12\",\"FAQ #42\"]",
                "",
                Instant.now(),
                Instant.now()
        );
    }

    public static AnswerDraftJpaEntity reviewedDraft(UUID inquiryId) {
        AnswerDraftJpaEntity entity = defaultDraft(inquiryId);
        entity.markReviewed("reviewer-user", "Looks good");
        return entity;
    }

    public static AnswerDraftJpaEntity approvedDraft(UUID inquiryId) {
        AnswerDraftJpaEntity entity = reviewedDraft(inquiryId);
        entity.markApproved("approver-user", "Approved for sending");
        return entity;
    }

    // ===== JSON Payloads =====

    public static String createInquiryJson() {
        return """
                {
                  "question": "qPCR 프로토콜 검증 요청",
                  "customerChannel": "email"
                }
                """;
    }

    public static String createInquiryJson(String question, String channel) {
        return String.format("""
                {
                  "question": "%s",
                  "customerChannel": "%s"
                }
                """, question, channel);
    }

    public static String createInquiryJsonWithTone(String question, String channel, String tone) {
        return String.format("""
                {
                  "question": "%s",
                  "customerChannel": "%s",
                  "preferredTone": "%s"
                }
                """, question, channel, tone);
    }

    public static String draftAnswerJson() {
        return """
                {
                  "question": "qPCR 프로토콜 검증 요청",
                  "tone": "professional",
                  "channel": "email"
                }
                """;
    }
}
