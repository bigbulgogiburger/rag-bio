package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.answer.orchestration.AnswerOrchestrationService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AnswerComposerService {

    private final AnswerOrchestrationService orchestrationService;
    private final AnswerDraftJpaRepository answerDraftRepository;

    public AnswerComposerService(AnswerOrchestrationService orchestrationService, AnswerDraftJpaRepository answerDraftRepository) {
        this.orchestrationService = orchestrationService;
        this.answerDraftRepository = answerDraftRepository;
    }

    public AnswerDraftResponse compose(UUID inquiryId, String question, String tone, String channel) {
        String normalizedTone = (tone == null || tone.isBlank()) ? "professional" : tone.trim().toLowerCase();
        String normalizedChannel = (channel == null || channel.isBlank()) ? "email" : channel.trim().toLowerCase();

        AnswerOrchestrationService.OrchestrationResult orchestration =
                orchestrationService.run(inquiryId, question, normalizedTone, normalizedChannel);
        AnalyzeResponse analysis = orchestration.analysis();

        List<String> citations = analysis.evidences().stream()
                .map(ev -> "chunk=" + ev.chunkId() + " score=" + String.format("%.3f", ev.score()))
                .toList();

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

        return toResponse(saved, orchestration.formatWarnings());
    }

    public AnswerDraftResponse review(UUID inquiryId, UUID answerId, String actor, String comment) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Answer draft not found"));

        if ("APPROVED".equals(entity.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Already approved answer cannot be reviewed");
        }

        entity.markReviewed(actor, comment);
        return toResponse(answerDraftRepository.save(entity), List.of());
    }

    public AnswerDraftResponse approve(UUID inquiryId, UUID answerId, String actor, String comment) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findByIdAndInquiryId(answerId, inquiryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Answer draft not found"));

        if (!"REVIEWED".equals(entity.getStatus()) && !"DRAFT".equals(entity.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Only draft/reviewed answer can be approved");
        }

        entity.markApproved(actor, comment);
        return toResponse(answerDraftRepository.save(entity), List.of());
    }

    public List<AnswerDraftResponse> history(UUID inquiryId) {
        return answerDraftRepository.findByInquiryIdOrderByVersionDesc(inquiryId).stream()
                .map(entity -> toResponse(entity, List.of()))
                .toList();
    }

    public AnswerDraftResponse latest(UUID inquiryId) {
        AnswerDraftJpaEntity entity = answerDraftRepository.findTopByInquiryIdOrderByVersionDesc(inquiryId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No answer draft found"));
        return toResponse(entity, List.of());
    }

    private AnswerDraftResponse toResponse(AnswerDraftJpaEntity entity, List<String> formatWarningsOverride) {
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
                formatWarnings
        );
    }

}
