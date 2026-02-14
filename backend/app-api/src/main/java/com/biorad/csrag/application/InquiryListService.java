package com.biorad.csrag.application;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.InquiryJpaEntity;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.InquirySpecifications;
import com.biorad.csrag.inquiry.infrastructure.persistence.jpa.SpringDataInquiryJpaRepository;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse;
import com.biorad.csrag.interfaces.rest.dto.InquiryListResponse.InquiryListItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 문의 목록 조회 서비스
 */
@Service
@Transactional(readOnly = true)
public class InquiryListService {

    private final SpringDataInquiryJpaRepository inquiryRepository;
    private final DocumentMetadataJpaRepository documentRepository;
    private final AnswerDraftJpaRepository answerRepository;

    public InquiryListService(
            SpringDataInquiryJpaRepository inquiryRepository,
            DocumentMetadataJpaRepository documentRepository,
            AnswerDraftJpaRepository answerRepository
    ) {
        this.inquiryRepository = inquiryRepository;
        this.documentRepository = documentRepository;
        this.answerRepository = answerRepository;
    }

    /**
     * 문의 목록을 페이징 및 필터링하여 조회
     *
     * @param statuses  상태 필터 (복수 가능)
     * @param channel   채널 필터
     * @param keyword   질문 키워드
     * @param from      시작 일시
     * @param to        종료 일시
     * @param pageable  페이징 정보
     * @return 페이징된 문의 목록
     */
    public InquiryListResponse list(
            List<String> statuses,
            String channel,
            String keyword,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Page<InquiryJpaEntity> page = inquiryRepository.findAll(
            InquirySpecifications.withFilters(statuses, channel, keyword, from, to),
            pageable
        );

        List<InquiryListItem> items = page.getContent().stream()
            .map(entity -> {
                // 문서 수 카운트
                int docCount = documentRepository.countByInquiryId(entity.getId());

                // 최신 답변 상태 조회
                String latestAnswerStatus = answerRepository
                    .findTopByInquiryIdOrderByVersionDesc(entity.getId())
                    .map(a -> a.getStatus())
                    .orElse(null);

                // 질문 요약 (200자 제한)
                String questionSummary = entity.getQuestion().length() > 200
                    ? entity.getQuestion().substring(0, 200) + "…"
                    : entity.getQuestion();

                return new InquiryListItem(
                    entity.getId(),
                    questionSummary,
                    entity.getCustomerChannel(),
                    entity.getStatus().name(),
                    docCount,
                    latestAnswerStatus,
                    entity.getCreatedAt()
                );
            })
            .toList();

        return new InquiryListResponse(
            items,
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
