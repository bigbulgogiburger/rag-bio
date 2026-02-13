package com.biorad.csrag.inquiry.infrastructure.persistence.jpa;

import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.model.InquiryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryRepositoryAdapterUnitTest {

    @Mock
    private SpringDataInquiryJpaRepository jpaRepository;

    @InjectMocks
    private InquiryRepositoryAdapter adapter;

    @Test
    void save_mapsDomainToEntity_andBackToDomain() {
        Inquiry inquiry = Inquiry.reconstitute(
                new InquiryId(UUID.randomUUID()),
                "질문",
                "email",
                InquiryStatus.RECEIVED,
                Instant.now()
        );

        ArgumentCaptor<InquiryJpaEntity> captor = ArgumentCaptor.forClass(InquiryJpaEntity.class);
        when(jpaRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Inquiry saved = adapter.save(inquiry);

        verify(jpaRepository).save(captor.getValue());
        assertThat(saved.getId()).isEqualTo(inquiry.getId());
        assertThat(saved.getQuestion()).isEqualTo(inquiry.getQuestion());
        assertThat(saved.getCustomerChannel()).isEqualTo(inquiry.getCustomerChannel());
        assertThat(saved.getStatus()).isEqualTo(inquiry.getStatus());
    }

    @Test
    void findById_returnsMappedDomain_whenExists() {
        UUID id = UUID.randomUUID();
        InquiryJpaEntity entity = InquiryJpaEntity.fromDomain(
                Inquiry.reconstitute(new InquiryId(id), "q", "email", InquiryStatus.IN_REVIEW, Instant.now())
        );
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity));

        Optional<Inquiry> result = adapter.findById(new InquiryId(id));

        assertThat(result).isPresent();
        assertThat(result.get().getId().value()).isEqualTo(id);
        assertThat(result.get().getStatus()).isEqualTo(InquiryStatus.IN_REVIEW);
    }
}
