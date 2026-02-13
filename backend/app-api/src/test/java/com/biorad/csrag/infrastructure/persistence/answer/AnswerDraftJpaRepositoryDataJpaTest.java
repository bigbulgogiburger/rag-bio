package com.biorad.csrag.infrastructure.persistence.answer;

import com.biorad.csrag.app.CsRagApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.test.context.ContextConfiguration(classes = CsRagApplication.class)
class AnswerDraftJpaRepositoryDataJpaTest {

    @Autowired
    private AnswerDraftJpaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void searchAuditLogs_filtersByStatusActorAndTimeRange_withPaging() {
        UUID inquiryId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update(
                "INSERT INTO inquiries(id, question, customer_channel, status, created_at) VALUES (?, ?, ?, ?, ?)",
                inquiryId,
                "q",
                "email",
                "RECEIVED",
                now
        );

        AnswerDraftJpaEntity d1 = new AnswerDraftJpaEntity(
                UUID.randomUUID(), inquiryId, 1, "CONDITIONAL", 0.4,
                "professional", "email", "DRAFT", "d1", "", "",
                now.minusSeconds(60), now.minusSeconds(60)
        );

        AnswerDraftJpaEntity d2 = new AnswerDraftJpaEntity(
                UUID.randomUUID(), inquiryId, 2, "SUPPORTED", 0.8,
                "professional", "email", "DRAFT", "d2", "", "",
                now.minusSeconds(30), now.minusSeconds(30)
        );
        d2.markReviewed("reviewer-1", "ok");

        AnswerDraftJpaEntity d3 = new AnswerDraftJpaEntity(
                UUID.randomUUID(), inquiryId, 3, "SUPPORTED", 0.9,
                "professional", "email", "DRAFT", "d3", "", "",
                now.minusSeconds(10), now.minusSeconds(10)
        );
        d3.markApproved("approver-1", "approved");

        repository.save(d1);
        repository.save(d2);
        repository.save(d3);

        Page<AnswerDraftJpaEntity> result = repository.searchAuditLogs(
                inquiryId,
                "APPROVED",
                "approver-1",
                now.minusSeconds(120),
                now,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("APPROVED");
        assertThat(result.getContent().get(0).getApprovedBy()).isEqualTo("approver-1");
    }
}
