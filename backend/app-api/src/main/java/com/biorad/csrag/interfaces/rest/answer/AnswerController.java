package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AiReviewResultJpaRepository;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaEntity;
import com.biorad.csrag.infrastructure.persistence.answer.AnswerDraftJpaRepository;
import com.biorad.csrag.interfaces.rest.answer.agent.AiApprovalResponse;
import com.biorad.csrag.interfaces.rest.answer.agent.AiReviewResponse;
import com.biorad.csrag.interfaces.rest.answer.agent.ApprovalAgentService;
import com.biorad.csrag.interfaces.rest.answer.agent.ApprovalResult;
import com.biorad.csrag.interfaces.rest.answer.agent.AutoWorkflowResponse;
import com.biorad.csrag.interfaces.rest.answer.agent.ReviewAgentService;
import com.biorad.csrag.interfaces.rest.answer.sender.SmtpEmailSender;
import com.biorad.csrag.interfaces.rest.answer.agent.ReviewIssue;
import com.biorad.csrag.interfaces.rest.answer.agent.ReviewResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.biorad.csrag.common.exception.NotFoundException;
import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.common.exception.ConflictException;
import com.biorad.csrag.common.exception.ForbiddenException;

@Tag(name = "Answer", description = "답변 초안 관리 API")
@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/answers")
public class AnswerController {

    private final InquiryRepository inquiryRepository;
    private final AnswerComposerService answerComposerService;
    private final AnswerDraftJpaRepository answerDraftRepository;
    private final AiReviewResultJpaRepository aiReviewResultRepository;
    private final ReviewAgentService reviewAgentService;
    private final ApprovalAgentService approvalAgentService;
    private final ObjectMapper objectMapper;
    private final SmtpEmailSender smtpEmailSender;

    public AnswerController(
            InquiryRepository inquiryRepository,
            AnswerComposerService answerComposerService,
            AnswerDraftJpaRepository answerDraftRepository,
            AiReviewResultJpaRepository aiReviewResultRepository,
            ReviewAgentService reviewAgentService,
            ApprovalAgentService approvalAgentService,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Autowired(required = false) SmtpEmailSender smtpEmailSender
    ) {
        this.inquiryRepository = inquiryRepository;
        this.answerComposerService = answerComposerService;
        this.answerDraftRepository = answerDraftRepository;
        this.aiReviewResultRepository = aiReviewResultRepository;
        this.reviewAgentService = reviewAgentService;
        this.approvalAgentService = approvalAgentService;
        this.objectMapper = objectMapper;
        this.smtpEmailSender = smtpEmailSender;
    }

    @Operation(summary = "답변 초안 생성", description = "RAG 파이프라인을 통해 답변 초안을 생성합니다")
    @ApiResponse(responseCode = "200", description = "초안 생성 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse draft(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @RequestBody(required = false) AnswerDraftRequest request
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        Inquiry inquiry = inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));

        String question = (request != null && request.question() != null && !request.question().isBlank())
                ? request.question()
                : inquiry.getQuestion();
        if (question == null || question.isBlank()) {
            throw new ValidationException("QUESTION_REQUIRED", "question is required: neither request body nor inquiry entity has a question");
        }

        String tone = (request != null && request.tone() != null && !request.tone().isBlank())
                ? request.tone()
                : inquiry.getPreferredTone();
        String channel = request != null ? request.channel() : null;
        String additionalInstructions = request != null ? request.additionalInstructions() : null;
        String previousAnswerId = request != null ? request.previousAnswerId() : null;

        return answerComposerService.compose(inquiryUuid, question, tone, channel, additionalInstructions, previousAnswerId);
    }

    @Operation(summary = "최신 답변 초안 조회", description = "가장 최근 답변 초안을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping("/latest")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse latest(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return answerComposerService.latest(inquiryUuid);
    }

    @Operation(summary = "답변 초안 이력 조회", description = "답변 초안의 버전 이력을 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping("/history")
    @ResponseStatus(HttpStatus.OK)
    public List<AnswerDraftResponse> history(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return answerComposerService.history(inquiryUuid);
    }

    @Operation(summary = "답변 이력 상세 조회", description = "답변 이력과 AI 리뷰 상세를 함께 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping("/history-detail")
    @ResponseStatus(HttpStatus.OK)
    public List<AnswerHistoryDetailResponse> historyDetail(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return answerComposerService.historyWithDetail(inquiryUuid);
    }

    @Operation(summary = "답변 초안 검토", description = "답변 초안을 검토합니다 (REVIEWER/ADMIN 권한 필요)")
    @ApiResponse(responseCode = "200", description = "검토 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @PostMapping("/{answerId}/review")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse review(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId,
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

    @Operation(summary = "답변 초안 승인", description = "답변 초안을 승인합니다 (APPROVER/ADMIN 권한 필요)")
    @ApiResponse(responseCode = "200", description = "승인 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @PostMapping("/{answerId}/approve")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse approve(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId,
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

    @Operation(summary = "답변 발송", description = "승인된 답변을 발송합니다 (SENDER/ADMIN 권한 필요)")
    @ApiResponse(responseCode = "200", description = "발송 성공")
    @ApiResponse(responseCode = "403", description = "권한 없음")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @PostMapping("/{answerId}/send")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse send(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId,
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

    @Operation(summary = "답변 초안 수정", description = "답변 초안 텍스트를 수정합니다")
    @ApiResponse(responseCode = "200", description = "수정 성공")
    @ApiResponse(responseCode = "404", description = "답변 초안을 찾을 수 없음")
    @ApiResponse(responseCode = "409", description = "이미 발송된 답변은 수정 불가")
    @PatchMapping("/{answerId}/edit-draft")
    @ResponseStatus(HttpStatus.OK)
    public AnswerDraftResponse editDraft(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId,
            @RequestBody EditDraftRequest request
    ) {
        if (request == null || request.draft() == null || request.draft().isBlank()) {
            throw new ValidationException("DRAFT_REQUIRED", "draft text is required");
        }

        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        UUID answerUuid = parseAnswerId(answerId);

        AnswerDraftJpaEntity entity = answerDraftRepository.findByIdAndInquiryId(answerUuid, inquiryUuid)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        if ("SENT".equals(entity.getStatus())) {
            throw new ConflictException("ANSWER_ALREADY_SENT", "Cannot edit a sent answer");
        }

        entity.updateDraft(request.draft());
        return answerComposerService.toResponsePublic(answerDraftRepository.save(entity));
    }

    @Operation(summary = "AI 리뷰 실행", description = "AI 에이전트가 답변 초안을 자동 리뷰합니다")
    @ApiResponse(responseCode = "200", description = "리뷰 완료")
    @ApiResponse(responseCode = "404", description = "답변 초안을 찾을 수 없음")
    @PostMapping("/{answerId}/ai-review")
    @ResponseStatus(HttpStatus.OK)
    public AiReviewResponse aiReview(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        return reviewAgentService.review(inquiryUuid, parseAnswerId(answerId));
    }

    @Operation(summary = "AI 승인 실행", description = "AI 에이전트가 답변 초안을 자동 승인 평가합니다")
    @ApiResponse(responseCode = "200", description = "승인 평가 완료")
    @ApiResponse(responseCode = "400", description = "AI 리뷰가 먼저 필요")
    @ApiResponse(responseCode = "404", description = "답변 초안을 찾을 수 없음")
    @PostMapping("/{answerId}/ai-approve")
    @ResponseStatus(HttpStatus.OK)
    public AiApprovalResponse aiApprove(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        UUID answerUuid = parseAnswerId(answerId);

        AnswerDraftJpaEntity draft = answerDraftRepository.findByIdAndInquiryId(answerUuid, inquiryUuid)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        AiReviewResultJpaEntity reviewEntity = aiReviewResultRepository.findByAnswerIdOrderByCreatedAtDesc(answerUuid)
                .stream().findFirst()
                .orElseThrow(() -> new ValidationException("AI_REVIEW_REQUIRED", "AI review must be completed first"));

        ReviewResult review = convertToReviewResult(reviewEntity);
        ApprovalResult result = approvalAgentService.evaluate(draft, review);

        draft.markAiApproved(result.decision(), result.reason());
        answerDraftRepository.save(draft);

        return new AiApprovalResponse(result.decision(), result.reason(), result.gateResults(), draft.getStatus(), "ai-approval-agent");
    }

    @Operation(summary = "AI 자동 워크플로우 실행", description = "AI 리뷰 + AI 승인을 한 번에 실행합니다")
    @ApiResponse(responseCode = "200", description = "워크플로우 완료")
    @ApiResponse(responseCode = "404", description = "답변 초안을 찾을 수 없음")
    @PostMapping("/{answerId}/auto-workflow")
    @ResponseStatus(HttpStatus.OK)
    public AutoWorkflowResponse autoWorkflow(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        UUID answerUuid = parseAnswerId(answerId);

        // Pre-validation: check status and run count
        AnswerDraftJpaEntity draft = answerDraftRepository.findByIdAndInquiryId(answerUuid, inquiryUuid)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        if ("SENT".equals(draft.getStatus())) {
            throw new ConflictException("ANSWER_ALREADY_SENT", "이미 발송된 답변은 재실행할 수 없습니다.");
        }
        if (draft.getWorkflowRunCount() >= 5) {
            throw new ValidationException("WORKFLOW_RUN_LIMIT", "워크플로우 실행은 최대 5회까지 가능합니다.");
        }
        if (!"DRAFT".equals(draft.getStatus())) {
            draft.resetForReReview();
        }
        draft.incrementWorkflowRunCount();
        answerDraftRepository.save(draft);

        // Step 1: AI Review
        AiReviewResponse reviewResponse = reviewAgentService.review(inquiryUuid, answerUuid);

        // Step 2: AI Approval
        draft = answerDraftRepository.findByIdAndInquiryId(answerUuid, inquiryUuid)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        AiReviewResultJpaEntity reviewEntity = aiReviewResultRepository.findByAnswerIdOrderByCreatedAtDesc(answerUuid)
                .stream().findFirst()
                .orElseThrow(() -> new NotFoundException("REVIEW_RESULT_NOT_FOUND", "Review result not found after AI review"));

        ReviewResult review = convertToReviewResult(reviewEntity);
        ApprovalResult approvalResult = approvalAgentService.evaluate(draft, review);

        draft.markAiApproved(approvalResult.decision(), approvalResult.reason());

        // Save gate results to review entity
        if (approvalResult.gateResults() != null) {
            try {
                String gateResultsJson = objectMapper.writeValueAsString(approvalResult.gateResults());
                reviewEntity.setGateResults(gateResultsJson);
                aiReviewResultRepository.save(reviewEntity);
            } catch (Exception e) {
                // gate results serialization failure is non-critical
            }
        }

        answerDraftRepository.save(draft);

        AiApprovalResponse approvalResponse = new AiApprovalResponse(
                approvalResult.decision(), approvalResult.reason(), approvalResult.gateResults(),
                draft.getStatus(), "ai-approval-agent"
        );

        boolean requiresHumanAction = !"AUTO_APPROVED".equals(approvalResult.decision());
        String summary = buildWorkflowSummary(reviewResponse, approvalResult);

        return new AutoWorkflowResponse(reviewResponse, approvalResponse, draft.getStatus(), requiresHumanAction, summary);
    }

    private ReviewResult convertToReviewResult(AiReviewResultJpaEntity entity) {
        List<ReviewIssue> issues;
        try {
            issues = objectMapper.readValue(
                    entity.getIssues() == null ? "[]" : entity.getIssues(),
                    new TypeReference<>() {}
            );
        } catch (Exception e) {
            issues = List.of();
        }
        return new ReviewResult(entity.getDecision(), entity.getScore(), issues, entity.getRevisedDraft(), entity.getSummary());
    }

    private String buildWorkflowSummary(AiReviewResponse review, ApprovalResult approval) {
        return String.format("AI 리뷰: %s (점수: %d) → AI 승인: %s",
                review.decision(), review.score(), approval.decision());
    }

    @Operation(summary = "답변 감사 로그 조회", description = "답변 초안의 감사 로그를 필터/페이징으로 조회합니다")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    @ApiResponse(responseCode = "404", description = "문의를 찾을 수 없음")
    @GetMapping("/audit-logs")
    @ResponseStatus(HttpStatus.OK)
    public AnswerAuditLogPageResponse auditLogs(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
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
            throw new ValidationException("INVALID_DATE_RANGE", "Invalid date range: from must be <= to");
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

    @Operation(summary = "이메일 미리보기", description = "답변 초안의 이메일 HTML 미리보기를 반환합니다")
    @ApiResponse(responseCode = "200", description = "미리보기 성공")
    @ApiResponse(responseCode = "404", description = "답변 초안을 찾을 수 없음")
    @GetMapping("/{answerId}/email-preview")
    @ResponseStatus(HttpStatus.OK)
    public EmailPreviewResponse emailPreview(
            @Parameter(description = "문의 ID (UUID)") @PathVariable String inquiryId,
            @Parameter(description = "답변 ID (UUID)") @PathVariable String answerId
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        ensureInquiryExists(inquiryUuid);
        UUID answerUuid = parseAnswerId(answerId);

        AnswerDraftJpaEntity draft = answerDraftRepository.findByIdAndInquiryId(answerUuid, inquiryUuid)
                .orElseThrow(() -> new NotFoundException("ANSWER_DRAFT_NOT_FOUND", "답변 초안을 찾을 수 없습니다."));

        if (smtpEmailSender != null) {
            String html = smtpEmailSender.renderTemplate(draft, inquiryUuid);
            return new EmailPreviewResponse(html, "[Bio-Rad CS] 기술지원 답변 - " + inquiryId.substring(0, 8));
        }

        // Fallback: return plain text when SMTP is not enabled
        return new EmailPreviewResponse(
                "<pre>" + escapeHtml(draft.getDraft()) + "</pre>",
                "[Bio-Rad CS] 기술지원 답변 - " + inquiryId.substring(0, 8)
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private void ensureInquiryExists(UUID inquiryUuid) {
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new NotFoundException("INQUIRY_NOT_FOUND", "문의를 찾을 수 없습니다."));
    }

    private String resolvePrincipalId(String userId, String actor) {
        if (userId != null && !userId.isBlank()) {
            return userId.trim();
        }
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        throw new ForbiddenException("AUTH_USER_ID_REQUIRED", "사용자 ID가 필요합니다.");
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
            throw new ForbiddenException(
                    "AUTH_ROLE_FORBIDDEN",
                    "principal=" + principalId + " required=" + String.join("|", requiredRoles)
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
            default -> throw new ValidationException("INVALID_SORT_FIELD", "Invalid sort field: " + requestedProperty);
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
            throw new ValidationException("INVALID_DATE_FORMAT", "Invalid " + field + " datetime format (ISO-8601 expected)");
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
            throw new ValidationException("INVALID_ANSWER_ID", "Invalid answerId format");
        }
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_INQUIRY_ID", "Invalid inquiryId format");
        }
    }
}
