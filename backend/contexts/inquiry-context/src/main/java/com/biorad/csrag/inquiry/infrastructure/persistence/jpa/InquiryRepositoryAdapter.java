package com.biorad.csrag.inquiry.infrastructure.persistence.jpa;

import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class InquiryRepositoryAdapter implements InquiryRepository {

    private final SpringDataInquiryJpaRepository jpaRepository;

    public InquiryRepositoryAdapter(SpringDataInquiryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Inquiry save(Inquiry inquiry) {
        InquiryJpaEntity saved = jpaRepository.save(InquiryJpaEntity.fromDomain(inquiry));
        return saved.toDomain();
    }

    @Override
    public Optional<Inquiry> findById(InquiryId inquiryId) {
        return jpaRepository.findById(inquiryId.value()).map(InquiryJpaEntity::toDomain);
    }
}
