package com.biorad.csrag.infrastructure.persistence.answer;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerDraftJpaEntityTest {

    @Test
    void markReviewed_updatesStatusAndReviewer() {
        AnswerDraftJpaEntity entity = createDraft();
        entity.markReviewed("reviewer-1", "Looks good");

        assertThat(entity.getStatus()).isEqualTo("REVIEWED");
        assertThat(entity.getReviewedBy()).isEqualTo("reviewer-1");
        assertThat(entity.getReviewComment()).isEqualTo("Looks good");
        assertThat(entity.getReviewedAt()).isNotNull();
    }

    @Test
    void markApproved_updatesStatusAndApprover() {
        AnswerDraftJpaEntity entity = createDraft();
        entity.markReviewed("reviewer", "ok");
        entity.markApproved("approver-1", "Approved for sending");

        assertThat(entity.getStatus()).isEqualTo("APPROVED");
        assertThat(entity.getApprovedBy()).isEqualTo("approver-1");
        assertThat(entity.getApproveComment()).isEqualTo("Approved for sending");
        assertThat(entity.getApprovedAt()).isNotNull();
    }

    @Test
    void markSent_updatesAllSendFields() {
        AnswerDraftJpaEntity entity = createDraft();
        entity.markSent("sender-1", "email", "msg-123", "req-456");

        assertThat(entity.getStatus()).isEqualTo("SENT");
        assertThat(entity.getSentBy()).isEqualTo("sender-1");
        assertThat(entity.getSendChannel()).isEqualTo("email");
        assertThat(entity.getSendMessageId()).isEqualTo("msg-123");
        assertThat(entity.getSendRequestId()).isEqualTo("req-456");
        assertThat(entity.getSentAt()).isNotNull();
    }

    @Test
    void markAiReviewed_setsReviewFields() {
        AnswerDraftJpaEntity entity = createDraft();
        entity.markAiReviewed("ai-reviewer", 85, "PASS", "No issues found");

        assertThat(entity.getStatus()).isEqualTo("REVIEWED");
        assertThat(entity.getReviewScore()).isEqualTo(85);
        assertThat(entity.getReviewDecision()).isEqualTo("PASS");
        assertThat(entity.getReviewedBy()).isEqualTo("ai-reviewer");
    }

    @Test
    void markAiApproved_autoApproved_setsApprovedStatus() {
        AnswerDraftJpaEntity entity = createDraft();
        entity.markAiApproved("AUTO_APPROVED", "All gates passed");

        assertThat(entity.getStatus()).isEqualTo("APPROVED");
        assertThat(entity.getApprovalDecision()).isEqualTo("AUTO_APPROVED");
        assertThat(entity.getApprovedBy()).isEqualTo("ai-approval-agent");
    }

    @Test
    void markAiApproved_rejected_revertsToDraft() {
        AnswerDraftJpaEntity entity = createDraft();
        entity.markAiApproved("REJECTED", "Score too low");

        assertThat(entity.getStatus()).isEqualTo("DRAFT");
        assertThat(entity.getApprovalDecision()).isEqualTo("REJECTED");
    }

    @Test
    void markAiApproved_escalated_keepsCurrentStatus() {
        AnswerDraftJpaEntity entity = createDraft();
        String originalStatus = entity.getStatus();
        entity.markAiApproved("ESCALATED", "Needs human review");

        assertThat(entity.getStatus()).isEqualTo(originalStatus);
        assertThat(entity.getApprovalDecision()).isEqualTo("ESCALATED");
    }

    @Test
    void updateDraft_changesText() {
        AnswerDraftJpaEntity entity = createDraft();
        Instant before = entity.getUpdatedAt();

        entity.updateDraft("Updated draft text");

        assertThat(entity.getDraft()).isEqualTo("Updated draft text");
        assertThat(entity.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void fullWorkflow_draftToSent() {
        AnswerDraftJpaEntity entity = createDraft();

        assertThat(entity.getStatus()).isEqualTo("DRAFT");

        entity.markReviewed("reviewer", "LGTM");
        assertThat(entity.getStatus()).isEqualTo("REVIEWED");

        entity.markApproved("approver", "Go ahead");
        assertThat(entity.getStatus()).isEqualTo("APPROVED");

        entity.markSent("sender", "email", "msg-1", "req-1");
        assertThat(entity.getStatus()).isEqualTo("SENT");
    }

    private AnswerDraftJpaEntity createDraft() {
        return new AnswerDraftJpaEntity(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                "SUPPORTED",
                0.85,
                "professional",
                "email",
                "DRAFT",
                "Test draft answer",
                "[\"ref1\"]",
                "",
                Instant.now(),
                Instant.now()
        );
    }
}
