package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaEntity;
import com.biorad.csrag.infrastructure.persistence.sendattempt.SendAttemptJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.answer.orchestration.AnswerOrchestrationService;
import com.biorad.csrag.interfaces.rest.answer.sender.MessageSender;
import com.biorad.csrag.interfaces.rest.answer.sender.SendCommand;
import com.biorad.csrag.interfaces.rest.answer.sender.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ConflictException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AnswerComposerService {

    private final AnswerOrchestrationService orchestrationService;
    private final AnswerDraftJpaRepository answerDraftRepository;
    private final SendAttemptJpaRepository sendAttemptRepository;
    private final List<MessageSender> messageSenders;

    public AnswerComposerService(
            AnswerOrchestrationService orchestrationService,
            AnswerDraftJpaRepository answerDraftRepository,
            SendAttemptJpaRepository sendAttemptRepository,
            List<MessageSender> messageSenders
    ) {
        this.orchestrationService = orchestrationService;
        this.answerDraftRepository = answerDraftRepository;
        this.sendAttemptRepository = sendAttemptRepository;
        this.messageSenders = messageSenders;
    }

    public AnswerDraftResponse compose(UUID inquiryId, String question, String tone, String channel) {
        String normalizedTone = (tone == null || tone.isBlank()) ? "professional" : tone.trim().toLowerCase();
        String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

        AnswerOrchestrationService.OrchestrationResult orchestration;
        AnalyzeResponse analysis;
        List<String> citations;
        List<String> formatWarnings;

        try {
            orchestration = orchestrationService.run(inquiryId, question, normalizedTone, normalizedChannel);
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

        AnswerDraftJpaEntity saved = answerDraftRepository.save(new AnswerDraftJpaEntity(
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
        ));

        return toResponse(saved, orchestration.formatWarnings(), analysis.translatedQuery());
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

    private String fallbackDraftByChannel(String channel) {
        return "messenger".equals(channel)
                ? "[요약]\n현재 자동 판정 단계 일부에 제한이 발생하여 보수적 안내를 제공드립니다.\n추가 자료 확인 후 답변 재생성을 요청하여 주시기 바랍니다."
                : "안녕하세요.\nBio-Rad CS 기술지원팀입니다.\n\n현재 자동 판정 단계 일부에 제한이 발생하여, 우선 보수적 안내를 제공드립니다.\n추가 자료(샘플 조건, 장비 설정 등)를 확인하신 후 답변 재생성을 요청하여 주시기 바랍니다.\n감사합니다.";
    }

    public AnswerDraftResponse toResponsePublic(AnswerDraftJpaEntity entity) {
        return toResponse(entity, List.of());
    }

    private AnswerDraftResponse toResponse(AnswerDraftJpaEntity entity, List<String> formatWarningsOverride) {
        return toResponse(entity, formatWarningsOverride, null);
    }

    private AnswerDraftResponse toResponse(AnswerDraftJpaEntity entity, List<String> formatWarningsOverride, String translatedQuery) {
        List<String> citations = entity.getCitations() == null || entity.getCitations().isBlank()
                ? List.of()
                : List.of(entity.getCitations().split("\\s*\\|\\s*"));

        List<String> riskFlags = entity.getRiskFlags() == null || entity.getRiskFlags().isBlank()
                ? List.of()
                : List.of(entity.getRiskFlags().split("\\s*,\\s*"));

        List<String> formatWarnings = formatWarningsOverride.isEmpty()
                ? List.of()
                : formatWarningsOverride;

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
                translatedQuery
        );
    }

}
