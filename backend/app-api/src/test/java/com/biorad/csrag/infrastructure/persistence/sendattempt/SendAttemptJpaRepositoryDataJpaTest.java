package com.biorad.csrag.infrastructure.persistence.sendattempt;

import com.biorad.csrag.app.CsRagApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@org.springframework.test.context.ContextConfiguration(classes = CsRagApplication.class)
class SendAttemptJpaRepositoryDataJpaTest {

    @Autowired
    private SendAttemptJpaRepository repository;

    @Test
    void countByOutcome_countsOnlyMatchingOutcome() {
        repository.save(new SendAttemptJpaEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "r1", "SENT", "m1", Instant.now()));
        repository.save(new SendAttemptJpaEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "r1", "DUPLICATE_BLOCKED", "dup", Instant.now()));
        repository.save(new SendAttemptJpaEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "r2", "DUPLICATE_BLOCKED", "dup", Instant.now()));

        long duplicateCount = repository.countByOutcome("DUPLICATE_BLOCKED");
        long sentCount = repository.countByOutcome("SENT");

        assertThat(duplicateCount).isEqualTo(2);
        assertThat(sentCount).isEqualTo(1);
    }
}
