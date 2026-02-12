package com.biorad.csrag.inquiry.infrastructure.persistence.jpa;

import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.model.InquiryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inquiries")
public class InquiryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "question", nullable = false, length = 4000)
    private String question;

    @Column(name = "customer_channel", nullable = false, length = 100)
    private String customerChannel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private InquiryStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InquiryJpaEntity() {
    }

    private InquiryJpaEntity(UUID id, String question, String customerChannel, InquiryStatus status, Instant createdAt) {
        this.id = id;
        this.question = question;
        this.customerChannel = customerChannel;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static InquiryJpaEntity fromDomain(Inquiry inquiry) {
        return new InquiryJpaEntity(
                inquiry.getId().value(),
                inquiry.getQuestion(),
                inquiry.getCustomerChannel(),
                inquiry.getStatus(),
                inquiry.getCreatedAt()
        );
    }

    public Inquiry toDomain() {
        return Inquiry.reconstitute(new InquiryId(id), question, customerChannel, status, createdAt);
    }
}
