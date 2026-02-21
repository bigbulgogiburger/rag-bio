package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaRepository;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaEntity;
import com.biorad.csrag.infrastructure.persistence.document.DocumentMetadataJpaRepository;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaEntity;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.answer.orchestration.AnswerOrchestrationService;
import com.biorad.csrag.interfaces.rest.answer.orchestration.SelfReviewStep;
import com.biorad.csrag.interfaces.rest.answer.sender.MessageSender;
import com.biorad.csrag.interfaces.rest.answer.sender.SendCommand;
import com.biorad.csrag.interfaces.rest.answer.sender.SendResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ConflictException;
import com.biorad.csrag.common.exception.ValidationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AnswerComposerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerComposerService.class);

    private final AnswerOrchestrationService orchestrationService;
    private final AnswerDraftJpaRepository answerDraftRepository;
    private final SendAttemptJpaRepository sendAttemptRepository;
    private final List<MessageSender> messageSenders;
    private final AiReviewResultJpaRepository aiReviewResultRepository;
    private final DocumentMetadataJpaRepository documentMetadataRepository;
    private final ObjectMapper objectMapper;

    public AnswerComposerService(
            AnswerOrchestrationService orchestrationService,
            AnswerDraftJpaRepository answerDraftRepository,
            SendAttemptJpaRepository sendAttemptRepository,
            List<MessageSender> messageSenders,
            AiReviewResultJpaRepository aiReviewResultRepository,
            DocumentMetadataJpaRepository documentMetadataRepository,
            ObjectMapper objectMapper
    ) {
        this.orchestrationService = orchestrationService;
        this.answerDraftRepository = answerDraftRepository;
        this.sendAttemptRepository = sendAttemptRepository;
        this.messageSenders = messageSenders;
        this.aiReviewResultRepository = aiReviewResultRepository;
        this.documentMetadataRepository = documentMetadataRepository;
        this.objectMapper = objectMapper;
    }

    public AnswerDraftResponse compose(UUID inquiryId, String question, String tone, String channel) {
        return compose(inquiryId, question, tone, channel, null, null);
    }

    public AnswerDraftResponse compose(UUID inquiryId, String question, String tone, String channel,
                                        String additionalInstructions, String previousAnswerId) {
        String normalizedTone = (tone == null || tone.isBlank()) ? "professional" : tone.trim().toLowerCase();
        String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

        // 인덱싱 가드: 미인덱싱 문서로 답변 생성 방지
        List<DocumentMetadataJpaEntity> docs = documentMetadataRepository.findByInquiryIdOrderByCreatedAtDesc(inquiryId);
        if (!docs.isEmpty()) {
            boolean anyIndexed = docs.stream()
                    .anyMatch(d -> "INDEXED".equals(d.getStatus()));
            if (!anyIndexed) {
                throw new ValidationException("NOT_INDEXED",
                        "첨부 문서의 인덱싱이 완료되지 않았습니다. 인덱싱 완료 후 답변 생성을 진행해 주세요.");
            }
        }

        // 보완 모드: 이전 답변 조회 및 보완 횟수 검증
        UUID prevId = parseUuidOrNull(previousAnswerId);
        String previousAnswerDraft = null;
        int refinementCount = 0;
        if (prevId != null) {
            AnswerDraftJpaEntity previousAnswer = answerDraftRepository.findByIdAndInquiryId(prevId, inquiryId)
                    .orElseThrow(() -> new NotFoundException("PREVIOUS_ANSWER_NOT_FOUND", "이전 답변을 찾을 수 없습니다."));
            previousAnswerDraft = previousAnswer.getDraft();
            refinementCount = previousAnswer.getRefinementCount() + 1;
            if (refinementCount > 5) {
                throw new ValidationException("REFINEMENT_LIMIT_EXCEEDED", "보완 요청은 최대 5회까지 가능합니다.");
            }
        }

        AnswerOrchestrationService.OrchestrationResult orchestration;
        AnalyzeResponse analysis;
        List<String> citations;
        List<String> formatWarnings;

        try {
            orchestration = orchestrationService.run(inquiryId, question, normalizedTone, normalizedChannel,
                    additionalInstructions, previousAnswerDraft);
            analysis = orchestration.analysis();
            citations = analysis.evidences().stream()
                    .map(ev -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("chunk=").append(ev.chunkId());
                        sb.append(" score=").append(String.format("%.3f", ev.score()));
                        sb.append(" documentId=").append(ev.documentId());
                        if (ev.fileName() != null) sb.append(" fileName=").append(ev.fileName());
                        if (ev.pageStart() != null) sb.append(" pageStart=").append(ev.pageStart());
                        if (ev.pageEnd() != null) sb.append(" pageEnd=").append(ev.pageEnd());
                        return sb.toString();
                    })
                    .toList();
            formatWarnings = orchestration.formatWarnings();
        } catch (RuntimeException ex) {
            analysis = new AnalyzeResponse(
                    inquiryId.toString(),
                    "CONDITIONAL",
                    0.0,
                    "일부 단계 실행에 실패해 보수적 초안으로 대체되었습니다.",
                    List.of("ORCHESTRATION_FALLBACK"),
                    List.of(),
                    null
            );
            citations = List.of();
            formatWarnings = List.of("FALLBACK_DRAFT_USED");
            orchestration = new AnswerOrchestrationService.OrchestrationResult(
                    analysis,
                    fallbackDraftByChannel(normalizedChannel),
                    formatWarnings
            );
        }

        int nextVersion = answerDraftRepository.findTopByInquiryIdOrderByVersionDesc(inquiryId)
                .map(x -> x.getVersion() + 1)
                .orElse(1);

        AnswerDraftJpaEntity entity = new AnswerDraftJpaEntity(
                UUID.randomUUID(),
                inquiryId,
                nextVersion,
                analysis.verdict(),
                analysis.confidence(),
                normalizedTone,
                normalizedChannel,
                "DRAFT",
                orchestration.draft(),
                String.join(" | ", citations),
                String.join(",", analysis.riskFlags()),
                Instant.now(),
                Instant.now()
        );

        if (prevId != null) {
            entity.setRefinementInfo(prevId, refinementCount, additionalInstructions);
        }

        AnswerDraftJpaEntity saved = answerDraftRepository.save(entity);
        return toResponse(saved, orchestration.formatWarnings(), analysis.translatedQuery(), orchestration.selfReviewIssues());
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_PREVIOUS_ANSWER_ID", "Invalid previousAnswerId format");
        }
    }

    public AnswerDraftResponse review(UUID inquiryId, UUID answerId, String actor, String comment) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        if ("APPROVED".equals(entity.getStatus())) {
            throw new ConflictException("ANSWER_ALREADY_APPROVED", "Already approved answer cannot be reviewed");
        }

        entity.markReviewed(actor, comment);
        return toResponse(answerDraftRepository.save(entity), List.of());
    }

    public AnswerDraftResponse approve(UUID inquiryId, UUID answerId, String actor, String comment) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        if (!"REVIEWED".equals(entity.getStatus()) && !"DRAFT".equals(entity.getStatus())) {
            throw new ConflictException("INVALID_ANSWER_STATUS", "Only draft/reviewed answer can be approved");
        }

        entity.markApproved(actor, comment);
        return toResponse(answerDraftRepository.save(entity), List.of());
    }

    @Transactional(readOnly = true)
    public List<AnswerDraftResponse> history(UUID inquiryId) {
        return answerDraftRepository.findByInquiryIdOrderByVersionDesc(inquiryId).stream()
                .map(entity -> toResponse(entity, List.of()))
                .toList();
    }

    public AnswerDraftResponse send(UUID inquiryId, UUID answerId, String actor, String channel, String sendRequestId) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        String requestId = (sendRequestId == null || sendRequestId.isBlank()) ? null : sendRequestId.trim();

        if (requestId != null && "SENT".equals(entity.getStatus()) && requestId.equals(entity.getSendRequestId())) {
            logSendAttempt(inquiryId, answerId, requestId, "DUPLICATE_BLOCKED", "already sent by same request id");
            return toResponse(entity, List.of());
        }

        if (!"APPROVED".equals(entity.getStatus())) {
            logSendAttempt(inquiryId, answerId, requestId, "REJECTED_NOT_APPROVED", "status=" + entity.getStatus());
            throw new ConflictException("ANSWER_NOT_APPROVED", "Only approved answer can be sent");
        }

        String normalizedChannel = (channel == null || channel.isBlank()) ? entity.getChannel() : channel.trim().toLowerCase();

        MessageSender sender = messageSenders.stream()
                .filter(s -> s.supports(normalizedChannel))
                .findFirst()
                .orElseThrow(() -> new ConflictException("UNSUPPORTED_CHANNEL", "Unsupported send channel: " + normalizedChannel));

        SendResult result = sender.send(new SendCommand(
                inquiryId,
                answerId,
                normalizedChannel,
                actor,
                entity.getDraft()
        ));

        entity.markSent(actor, normalizedChannel, result.messageId(), requestId);
        logSendAttempt(inquiryId, answerId, requestId, "SENT", result.messageId());
        return toResponse(answerDraftRepository.save(entity), List.of());
    }

    @Transactional(readOnly = true)
    public AnswerDraftResponse latest(UUID inquiryId) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findTopByInquiryIdOrderByVersionDesc(inquiryId)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));
        return toResponse(entity, List.of());
    }

    private void logSendAttempt(UUID inquiryId, UUID answerId, String requestId, String outcome, String detail) {
        sendAttemptRepository.save(new SendAttemptJpaEntity(
                UUID.randomUUID(),
                inquiryId,
                answerId,
                requestId,
                outcome,
                detail,
                Instant.now()
        ));
    }

    @Transactional(readOnly = true)
    public List<AnswerHistoryDetailResponse> historyWithDetail(UUID inquiryId) {
        List<AnswerDraftJpaEntity> drafts = answerDraftRepository.findByInquiryIdOrderByVersionDesc(inquiryId);
        return drafts.stream().map(draft -> {
            AnswerDraftResponse response = toResponse(draft, List.of());
            List<AiReviewResultJpaEntity> reviews = aiReviewResultRepository.findByAnswerIdOrderByCreatedAtDesc(draft.getId());
            List<AnswerHistoryDetailResponse.AiReviewHistoryItem> reviewItems = reviews.stream().map(r -> {
                List<AnswerHistoryDetailResponse.ReviewIssueItem> issues = parseIssues(r.getIssues());
                List<AnswerHistoryDetailResponse.GateResultItem> gates = parseGateResults(r.getGateResults());
                return new AnswerHistoryDetailResponse.AiReviewHistoryItem(
                    r.getId().toString(), r.getDecision(), r.getScore(), r.getSummary(),
                    r.getRevisedDraft(), issues, gates, r.getCreatedAt().toString()
                );
            }).toList();
            return new AnswerHistoryDetailResponse(response, reviewItems);
        }).toList();
    }

    private List<AnswerHistoryDetailResponse.ReviewIssueItem> parseIssues(String issuesJson) {
        if (issuesJson == null || issuesJson.isBlank()) return List.of();
        try {
            List<java.util.Map<String, String>> items = objectMapper.readValue(issuesJson, new TypeReference<>() {});
            return items.stream().map(m -> new AnswerHistoryDetailResponse.ReviewIssueItem(
                    m.getOrDefault("category", ""),
                    m.getOrDefault("severity", ""),
                    m.getOrDefault("description", ""),
                    m.getOrDefault("suggestion", "")
            )).toList();
        } catch (Exception e) {
            log.warn("Failed to parse review issues JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private List<AnswerHistoryDetailResponse.GateResultItem> parseGateResults(String gateResultsJson) {
        if (gateResultsJson == null || gateResultsJson.isBlank()) return List.of();
        try {
            List<java.util.Map<String, Object>> items = objectMapper.readValue(gateResultsJson, new TypeReference<>() {});
            return items.stream().map(m -> new AnswerHistoryDetailResponse.GateResultItem(
                    String.valueOf(m.getOrDefault("gate", "")),
                    Boolean.TRUE.equals(m.get("passed")),
                    String.valueOf(m.getOrDefault("actualValue", "")),
                    String.valueOf(m.getOrDefault("threshold", ""))
            )).toList();
        } catch (Exception e) {
            log.warn("Failed to parse gate results JSON: {}", e.getMessage());
            return List.of();
        }
    }

    private String fallbackDraftByChannel(String channel) {
        return "messenger".equals(channel)
                ? "[요약]\n현재 자동 판정 단계 일부에 제한이 발생하여 보수적 안내를 제공드립니다.\n추가 자료 확인 후 답변 재생성을 요청하여 주시기 바랍니다."
                : "안녕하세요.\nBio-Rad CS 기술지원팀입니다.\n\n현재 자동 판정 단계 일부에 제한이 발생하여, 우선 보수적 안내를 제공드립니다.\n추가 자료(샘플 조건, 장비 설정 등)를 확인하신 후 답변 재생성을 요청하여 주시기 바랍니다.\n감사합니다.";
    }

    public AnswerDraftResponse toResponsePublic(AnswerDraftJpaEntity entity) {
        return toResponse(entity, List.of());
    }

    private AnswerDraftResponse toResponse(AnswerDraftJpaEntity entity, List<String> formatWarningsOverride) {
        return toResponse(entity, formatWarningsOverride, null, List.of());
    }

    private AnswerDraftResponse toResponse(AnswerDraftJpaEntity entity, List<String> formatWarningsOverride,
                                            String translatedQuery, List<SelfReviewStep.QualityIssue> selfReviewIssues) {
        List<String> citations = entity.getCitations() == null || entity.getCitations().isBlank()
                ? List.of()
                : List.of(entity.getCitations().split("\\s*\\|\\s*"));

        List<String> riskFlags = entity.getRiskFlags() == null || entity.getRiskFlags().isBlank()
                ? List.of()
                : List.of(entity.getRiskFlags().split("\\s*,\\s*"));

        List<String> formatWarnings = formatWarningsOverride.isEmpty()
                ? List.of()
                : formatWarningsOverride;

        List<AnswerDraftResponse.SelfReviewIssueResponse> issueResponses = selfReviewIssues == null
                ? List.of()
                : selfReviewIssues.stream()
                    .map(i -> new AnswerDraftResponse.SelfReviewIssueResponse(
                            i.category(), i.severity(), i.description(), i.suggestion()))
                    .toList();

        return new AnswerDraftResponse(
                entity.getId().toString(),
                entity.getInquiryId().toString(),
                entity.getVersion(),
                entity.getStatus(),
                entity.getVerdict(),
                entity.getConfidence(),
                entity.getDraft(),
                citations,
                riskFlags,
                entity.getTone(),
                entity.getChannel(),
                entity.getReviewedBy(),
                entity.getReviewComment(),
                entity.getApprovedBy(),
                entity.getApproveComment(),
                entity.getSentBy(),
                entity.getSendChannel(),
                entity.getSendMessageId(),
                formatWarnings,
                entity.getReviewScore(),
                entity.getReviewDecision(),
                entity.getApprovalDecision(),
                entity.getApprovalReason(),
                translatedQuery,
                entity.getPreviousAnswerId() != null ? entity.getPreviousAnswerId().toString() : null,
                entity.getRefinementCount(),
                issueResponses,
                entity.getWorkflowRunCount(),
                entity.getAdditionalInstructions()
        );
    }

}
