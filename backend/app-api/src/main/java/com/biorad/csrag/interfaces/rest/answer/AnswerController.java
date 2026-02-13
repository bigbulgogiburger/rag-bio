package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/answers")
public class AnswerController {

    private final InquiryRepository inquiryRepository;
    private final AnswerComposerService answerComposerService;
    private final AnswerDraftJpaRepository answerDraftRepository;

    public AnswerController(
            InquiryRepository inquiryRepository,
            AnswerComposerService answerComposerService,
            AnswerDraftJpaRepository answerDraftRepository
    ) {
        this.inquiryRepository = inquiryRepository;
        this.answerComposerService = answerComposerService;
        this.answerDraftRepository = answerDraftRepository;
    }

    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse draft(
            @PathVariable String inquiryId,
            @Valid @RequestBody AnswerDraftRequest request
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return answerComposerService.compose(inquiryUuid, request.question(), request.tone(), request.channel());
    }

    @GetMapping("/latest")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse latest(@PathVariable String inquiryId) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return answerComposerService.latest(inquiryUuid);
    }

    @GetMapping("/history")
    @ResponseStatus(HttpStatus.OK)
    public List<AnswerDraftResponse> history(@PathVariable String inquiryId) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return answerComposerService.history(inquiryUuid);
    }

    @PostMapping("/{answerId}/review")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse review(
            @PathVariable String inquiryId,
            @PathVariable String answerId,
            @RequestBody(required = false) AnswerActionRequest request
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        String actor = request == null ? null : request.actor();
        String comment = request == null ? null : request.comment();
        return answerComposerService.review(inquiryUuid, parseAnswerId(answerId), actor, comment);
    }

    @PostMapping("/{answerId}/approve")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse approve(
            @PathVariable String inquiryId,
            @PathVariable String answerId,
            @RequestBody(required = false) AnswerActionRequest request,
            @RequestHeader(name = "X-Role", required = false) String role
    ) {
        requireRole(role, "APPROVER");
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        String actor = request == null ? null : request.actor();
        String comment = request == null ? null : request.comment();
        return answerComposerService.approve(inquiryUuid, parseAnswerId(answerId), actor, comment);
    }

    @PostMapping("/{answerId}/send")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse send(
            @PathVariable String inquiryId,
            @PathVariable String answerId,
            @RequestBody(required = false) SendAnswerRequest request,
            @RequestHeader(name = "X-Role", required = false) String role
    ) {
        requireRole(role, "SENDER");
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        String actor = request == null ? null : request.actor();
        String channel = request == null ? null : request.channel();
        String sendRequestId = request == null ? null : request.sendRequestId();
        return answerComposerService.send(inquiryUuid, parseAnswerId(answerId), actor, channel, sendRequestId);
    }

    @GetMapping("/audit-logs")
    @ResponseStatus(HttpStatus.OK)
    public List<AnswerAuditLogResponse> auditLogs(@PathVariable String inquiryId) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);

        return answerDraftRepository.findByInquiryIdOrderByVersionDesc(inquiryUuid)
                .stream()
                .map(a -> new AnswerAuditLogResponse(
                        a.getId().toString(),
                        a.getVersion(),
                        a.getStatus(),
                        a.getReviewedBy(),
                        a.getReviewComment(),
                        a.getApprovedBy(),
                        a.getApproveComment(),
                        a.getSentBy(),
                        a.getSendChannel(),
                        a.getSendMessageId(),
                        a.getCreatedAt(),
                        a.getUpdatedAt()
                ))
                .toList();
    }

    private void ensureInquiryExists(UUID inquiryUuid) {
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
    }

    private void requireRole(String role, String requiredRole) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!requiredRole.equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing required role: " + requiredRole);
        }
    }

    private UUID parseAnswerId(String answerId) {
        try {
            return UUID.fromString(answerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid answerId format");
        }
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inquiryId format");
        }
    }
}
