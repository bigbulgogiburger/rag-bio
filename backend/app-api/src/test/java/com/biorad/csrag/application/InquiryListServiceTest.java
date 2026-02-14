package com.biorad.csrag.application;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.inquiry.domain.model.InquiryStatus;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.InquiryJpaEntity;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.SpringDataInquiryJpaRepository;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * InquiryListService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class InquiryListServiceTest {

    @Mock
    private SpringDataInquiryJpaRepository inquiryRepository;

    @Mock
    private DocumentMetadataJpaRepository documentRepository;

    @Mock
    private AnswerDraftJpaRepository answerRepository;

    private InquiryListService service;

    @BeforeEach
    void setUp() {
        service = new InquiryListService(inquiryRepository, documentRepository, answerRepository);
    }

    @Test
    void list_returnsEmptyPage_whenNoInquiries() {
        // given
        Pageable pageable = PageRequest.of(0, 20);
        Page<InquiryJpaEntity> emptyPage = new PageImpl<>(Collections.emptyList(), pageable, 0);
        when(inquiryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(emptyPage);

        // when
        InquiryListResponse response = service.list(null, null, null, null, null, pageable);

        // then
        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void list_returnsSingleInquiry_withDocumentCountAndAnswerStatus() {
        // given
        UUID inquiryId = UUID.randomUUID();
        InquiryJpaEntity entity = createMockEntity(inquiryId, "Test question?", "email", InquiryStatus.RECEIVED);
        Page<InquiryJpaEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        Pageable pageable = PageRequest.of(0, 20);

        when(inquiryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(documentRepository.countByInquiryId(inquiryId)).thenReturn(3);
        AnswerDraftJpaEntity answerDraft = createMockAnswerDraft(inquiryId, "APPROVED");
        when(answerRepository.findTopByInquiryIdOrderByVersionDesc(inquiryId))
            .thenReturn(Optional.of(answerDraft));

        // when
        InquiryListResponse response = service.list(null, null, null, null, null, pageable);

        // then
        assertThat(response.content()).hasSize(1);
        InquiryListResponse.InquiryListItem item = response.content().get(0);
        assertThat(item.inquiryId()).isEqualTo(inquiryId);
        assertThat(item.question()).isEqualTo("Test question?");
        assertThat(item.customerChannel()).isEqualTo("email");
        assertThat(item.status()).isEqualTo("RECEIVED");
        assertThat(item.documentCount()).isEqualTo(3);
        assertThat(item.latestAnswerStatus()).isEqualTo("APPROVED");
    }

    @Test
    void list_truncatesLongQuestion_toMaxLength200() {
        // given
        String longQuestion = "a".repeat(250);
        UUID inquiryId = UUID.randomUUID();
        InquiryJpaEntity entity = createMockEntity(inquiryId, longQuestion, "email", InquiryStatus.RECEIVED);
        Page<InquiryJpaEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        Pageable pageable = PageRequest.of(0, 20);

        when(inquiryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(documentRepository.countByInquiryId(inquiryId)).thenReturn(0);
        when(answerRepository.findTopByInquiryIdOrderByVersionDesc(inquiryId)).thenReturn(Optional.empty());

        // when
        InquiryListResponse response = service.list(null, null, null, null, null, pageable);

        // then
        InquiryListResponse.InquiryListItem item = response.content().get(0);
        assertThat(item.question()).hasSize(201);  // 200 chars + "…"
        assertThat(item.question()).endsWith("…");
    }

    @Test
    void list_returnsNullLatestAnswerStatus_whenNoAnswerDraft() {
        // given
        UUID inquiryId = UUID.randomUUID();
        InquiryJpaEntity entity = createMockEntity(inquiryId, "Question", "messenger", InquiryStatus.RECEIVED);
        Page<InquiryJpaEntity> page = new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1);
        Pageable pageable = PageRequest.of(0, 20);

        when(inquiryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(documentRepository.countByInquiryId(inquiryId)).thenReturn(0);
        when(answerRepository.findTopByInquiryIdOrderByVersionDesc(inquiryId)).thenReturn(Optional.empty());

        // when
        InquiryListResponse response = service.list(null, null, null, null, null, pageable);

        // then
        InquiryListResponse.InquiryListItem item = response.content().get(0);
        assertThat(item.latestAnswerStatus()).isNull();
    }

    @Test
    void list_returnsPagedResults_withCorrectMetadata() {
        // given
        List<InquiryJpaEntity> entities = List.of(
            createMockEntity(UUID.randomUUID(), "Q1", "email", InquiryStatus.RECEIVED),
            createMockEntity(UUID.randomUUID(), "Q2", "email", InquiryStatus.IN_REVIEW)
        );
        Pageable pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InquiryJpaEntity> page = new PageImpl<>(entities, pageable, 42);

        when(inquiryRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        entities.forEach(e -> {
            when(documentRepository.countByInquiryId(e.getId())).thenReturn(0);
            when(answerRepository.findTopByInquiryIdOrderByVersionDesc(e.getId())).thenReturn(Optional.empty());
        });

        // when
        InquiryListResponse response = service.list(null, null, null, null, null, pageable);

        // then
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(42);
        assertThat(response.totalPages()).isEqualTo(3);  // ceil(42 / 20)
    }

    /**
     * 모의 InquiryJpaEntity 생성 헬퍼
     */
    private InquiryJpaEntity createMockEntity(UUID id, String question, String channel, InquiryStatus status) {
        InquiryJpaEntity entity = org.mockito.Mockito.mock(InquiryJpaEntity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.lenient().when(entity.getId()).thenReturn(id);
        org.mockito.Mockito.lenient().when(entity.getQuestion()).thenReturn(question);
        org.mockito.Mockito.lenient().when(entity.getCustomerChannel()).thenReturn(channel);
        org.mockito.Mockito.lenient().when(entity.getStatus()).thenReturn(status);
        org.mockito.Mockito.lenient().when(entity.getCreatedAt()).thenReturn(Instant.now());
        return entity;
    }

    /**
     * 모의 AnswerDraftJpaEntity 생성 헬퍼
     */
    private AnswerDraftJpaEntity createMockAnswerDraft(UUID inquiryId, String status) {
        AnswerDraftJpaEntity entity = org.mockito.Mockito.mock(AnswerDraftJpaEntity.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.lenient().when(entity.getInquiryId()).thenReturn(inquiryId);
        org.mockito.Mockito.lenient().when(entity.getStatus()).thenReturn(status);
        return entity;
    }
}
