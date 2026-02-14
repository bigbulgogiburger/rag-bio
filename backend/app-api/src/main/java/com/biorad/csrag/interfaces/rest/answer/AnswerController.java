package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.server.ResponseStatusException;

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
            @RequestBody(required = false) AnswerActionRequest request,
            @RequestHeader(name = "X-User-Id", required = false) String userId,
            @RequestHeader(name = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(name = "X-Role", required = false) String legacyRole
    ) {
        String actor = request == null ? null : request.actor();
        String principalId = resolvePrincipalId(userId, actor);
        requireAnyRole(principalId, userRoles, legacyRole, "REVIEWER", "ADMIN");

        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        String comment = request == null ? null : request.comment();
        return answerComposerService.review(inquiryUuid, parseAnswerId(answerId), principalId, comment);
    }

    @PostMapping("/{answerId}/approve")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse approve(
            @PathVariable String inquiryId,
            @PathVariable String answerId,
            @RequestBody(required = false) AnswerActionRequest request,
            @RequestHeader(name = "X-User-Id", required = false) String userId,
            @RequestHeader(name = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(name = "X-Role", required = false) String legacyRole
    ) {
        String actor = request == null ? null : request.actor();
        String principalId = resolvePrincipalId(userId, actor);
        requireAnyRole(principalId, userRoles, legacyRole, "APPROVER", "ADMIN");

        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        String comment = request == null ? null : request.comment();
        return answerComposerService.approve(inquiryUuid, parseAnswerId(answerId), principalId, comment);
    }

    @PostMapping("/{answerId}/send")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse send(
            @PathVariable String inquiryId,
            @PathVariable String answerId,
            @RequestBody(required = false) SendAnswerRequest request,
            @RequestHeader(name = "X-User-Id", required = false) String userId,
            @RequestHeader(name = "X-User-Roles", required = false) String userRoles,
            @RequestHeader(name = "X-Role", required = false) String legacyRole
    ) {
        String actor = request == null ? null : request.actor();
        String principalId = resolvePrincipalId(userId, actor);
        requireAnyRole(principalId, userRoles, legacyRole, "SENDER", "ADMIN");

        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        String channel = request == null ? null : request.channel();
        String sendRequestId = request == null ? null : request.sendRequestId();
        return answerComposerService.send(inquiryUuid, parseAnswerId(answerId), principalId, channel, sendRequestId);
    }

    @GetMapping("/audit-logs")
    @ResponseStatus(HttpStatus.OK)
    public AnswerAuditLogPageResponse auditLogs(
            @PathVariable String inquiryId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);

        Instant fromTs = parseInstant(from, "from");
        Instant toTs = parseInstant(to, "to");
        if (fromTs != null && toTs != null && fromTs.isAfter(toTs)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range: from must be <= to");
        }

        String normalizedStatus = normalizeNullable(status);
        String normalizedActor = normalizeNullable(actor);
        Pageable pageable = buildPageable(page, size, sort);

        var result = answerDraftRepository.searchAuditLogs(inquiryUuid, normalizedStatus, normalizedActor, fromTs, toTs, pageable);
        List<AnswerAuditLogResponse> items = result.stream()
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

        return new AnswerAuditLogPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private void ensureInquiryExists(UUID inquiryUuid) {
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));
    }

    private String resolvePrincipalId(String userId, String actor) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "AUTH_USER_ID_REQUIRED");
    }

    private void requireAnyRole(String principalId, String userRolesHeader, String legacyRole, String... requiredRoles) {
        Set<String> roles = Arrays.stream(((userRolesHeader == null ? "" : userRolesHeader) + "," + (legacyRole == null ? "" : legacyRole)).split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());

        boolean allowed = Arrays.stream(requiredRoles)
                .map(String::toUpperCase)
                .anyMatch(roles::contains);

        if (!allowed) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "AUTH_ROLE_FORBIDDEN principal=" + principalId + " required=" + String.join("|", requiredRoles)
            );
        }
    }

    private Pageable buildPageable(int page, int size, String sort) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = Math.min(Math.max(size, 1), 200);

        String[] parts = (sort == null ? "" : sort).split(",");
        String requestedProperty = parts.length > 0 && !parts[0].isBlank() ? parts[0].trim() : "createdAt";
        String property = switch (requestedProperty) {
            case "createdAt", "updatedAt", "status", "version" -> requestedProperty;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort field: " + requestedProperty);
        };

        String direction = parts.length > 1 ? parts[1].trim().toLowerCase() : "desc";
        Sort.Direction dir = "asc".equals(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;

        return PageRequest.of(normalizedPage, normalizedSize, Sort.by(dir, property));
    }

    private Instant parseInstant(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field + " datetime format (ISO-8601 expected)");
        }
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
