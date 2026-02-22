package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.biorad.csrag.interfaces.rest.search.ProductExtractorService;
import com.biorad.csrag.interfaces.rest.search.ProductFamilyRegistry;
import com.biorad.csrag.interfaces.rest.search.SearchFilter;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AnswerOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerOrchestrationService.class);
    private static final int MAX_RECOMPOSE_ATTEMPTS = 2;

    private final RetrieveStep retrieveStep;
    private final VerifyStep verifyStep;
    private final ComposeStep composeStep;
    private final SelfReviewStep selfReviewStep;
    private final OrchestrationRunJpaRepository runRepository;
    private final SseService sseService;
    private final QuestionDecomposerService questionDecomposerService;
    private final ProductExtractorService productExtractorService;
    private final ProductFamilyRegistry productFamilyRegistry;

    public AnswerOrchestrationService(
            RetrieveStep retrieveStep,
            VerifyStep verifyStep,
            ComposeStep composeStep,
            SelfReviewStep selfReviewStep,
            OrchestrationRunJpaRepository runRepository,
            SseService sseService,
            QuestionDecomposerService questionDecomposerService,
            ProductExtractorService productExtractorService,
            ProductFamilyRegistry productFamilyRegistry
    ) {
        this.retrieveStep = retrieveStep;
        this.verifyStep = verifyStep;
        this.composeStep = composeStep;
        this.selfReviewStep = selfReviewStep;
        this.runRepository = runRepository;
        this.sseService = sseService;
        this.questionDecomposerService = questionDecomposerService;
        this.productExtractorService = productExtractorService;
        this.productFamilyRegistry = productFamilyRegistry;
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel) {
        return run(inquiryId, question, tone, channel, null, null);
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel,
                                    String additionalInstructions, String previousAnswerDraft) {

        // ── DECOMPOSE ──────────────────────────────────────────────
        emitPipelineEvent(inquiryId, "DECOMPOSE", "STARTED", null);
        DecomposedQuestion decomposed = executeWithRunLog(inquiryId, "DECOMPOSE",
                () -> questionDecomposerService.decompose(question));

        // extractAll: 복수 제품 추출
        List<ProductExtractorService.ExtractedProduct> extractedProducts =
                productExtractorService.extractAll(question);
        Set<String> extractedFamilies = extractedProducts.stream()
                .map(ProductExtractorService.ExtractedProduct::productFamily)
                .collect(Collectors.toSet());

        SearchFilter filter = (!extractedFamilies.isEmpty())
                ? SearchFilter.forProducts(inquiryId, extractedFamilies)
                : SearchFilter.forInquiry(inquiryId);

        List<SubQuestion> subQuestions = decomposed.subQuestions();
        boolean isMultiQuestion = subQuestions.size() > 1;
        emitPipelineEvent(inquiryId, "DECOMPOSE", "COMPLETED",
                isMultiQuestion ? "하위 질문 " + subQuestions.size() + "개 분해" : null);

        // ── RETRIEVE (3-level fallback) ──────────────────────────
        emitPipelineEvent(inquiryId, "RETRIEVE", "STARTED",
                isMultiQuestion ? "하위 질문 " + subQuestions.size() + "개 개별 검색" : null);

        List<EvidenceItem> retrievedEvidences;
        List<PerQuestionEvidence> perQuestionEvidences = null;
        RetrievalQuality retrievalQuality;

        // Level 0: 추출된 제품 필터로 검색
        RetrieveResult level0 = doRetrieveWithFilter(inquiryId, question, subQuestions, isMultiQuestion, filter);
        retrievedEvidences = level0.evidences;
        perQuestionEvidences = level0.perQuestion;

        if (!retrievedEvidences.isEmpty()) {
            retrievalQuality = RetrievalQuality.EXACT;
        } else if (filter.hasProductFilter()) {
            // Level 1: 카테고리 확장 검색
            Set<String> expandedFamilies = productFamilyRegistry.expand(extractedFamilies);
            if (!expandedFamilies.equals(extractedFamilies) && !expandedFamilies.isEmpty()) {
                log.info("Level 0 yielded 0 results, expanding to category families={} inquiryId={}",
                        expandedFamilies, inquiryId);
                SearchFilter expandedFilter = SearchFilter.forProducts(inquiryId, expandedFamilies);
                RetrieveResult level1 = doRetrieveWithFilter(
                        inquiryId, question, subQuestions, isMultiQuestion, expandedFilter);
                retrievedEvidences = level1.evidences;
                perQuestionEvidences = level1.perQuestion;
            }

            if (!retrievedEvidences.isEmpty()) {
                retrievalQuality = RetrievalQuality.CATEGORY_EXPANDED;
            } else {
                // Level 2: 필터 없이 전체 검색
                log.warn("Category expansion yielded 0 results, retrying unfiltered inquiryId={}", inquiryId);
                SearchFilter unfilteredFilter = SearchFilter.forInquiry(inquiryId);
                RetrieveResult level2 = doRetrieveWithFilter(
                        inquiryId, question, subQuestions, isMultiQuestion, unfilteredFilter);
                retrievedEvidences = level2.evidences;
                perQuestionEvidences = level2.perQuestion;
                retrievalQuality = RetrievalQuality.UNFILTERED;
            }
        } else {
            // 제품 추출 실패 시 필터 없이 검색한 것이므로 UNFILTERED
            retrievalQuality = RetrievalQuality.UNFILTERED;
        }

        emitPipelineEvent(inquiryId, "RETRIEVE", "COMPLETED",
                "retrievalQuality=" + retrievalQuality);

        // ── VERIFY ─────────────────────────────────────────────────
        final List<EvidenceItem> allEvidences = retrievedEvidences;
        emitPipelineEvent(inquiryId, "VERIFY", "STARTED", null);
        AnalyzeResponse analysis = executeWithRunLog(inquiryId, "VERIFY",
                () -> verifyStep.execute(inquiryId, question, allEvidences));
        emitPipelineEvent(inquiryId, "VERIFY", "COMPLETED", null);

        // ── COMPOSE ────────────────────────────────────────────────
        String mergedInstructions = buildMergedInstructions(additionalInstructions, perQuestionEvidences);

        emitPipelineEvent(inquiryId, "COMPOSE", "STARTED", null);
        ComposeStep.ComposeStepResult composed = executeWithRunLog(inquiryId, "COMPOSE",
                () -> composeStep.execute(analysis, tone, channel, mergedInstructions, previousAnswerDraft));
        emitPipelineEvent(inquiryId, "COMPOSE", "COMPLETED", null);

        // ── SELF_REVIEW ────────────────────────────────────────────
        String finalDraft = composed.draft();
        List<String> finalWarnings = composed.formatWarnings();
        List<SelfReviewStep.QualityIssue> selfReviewIssues = List.of();

        emitPipelineEvent(inquiryId, "SELF_REVIEW", "STARTED", null);
        try {
            SelfReviewStep.SelfReviewResult reviewResult = executeWithRunLog(
                    inquiryId, "SELF_REVIEW",
                    () -> selfReviewStep.review(composed.draft(), analysis.evidences(), question)
            );

            if (reviewResult.passed()) {
                selfReviewIssues = reviewResult.issues();
            } else {
                String currentDraft = composed.draft();
                SelfReviewStep.SelfReviewResult latestReview = reviewResult;

                for (int attempt = 1; attempt <= MAX_RECOMPOSE_ATTEMPTS; attempt++) {
                    log.info("self-review retry attempt={} inquiryId={}", attempt, inquiryId);
                    emitPipelineEvent(inquiryId, "SELF_REVIEW", "RETRY",
                            "재작성 시도 " + attempt + "/" + MAX_RECOMPOSE_ATTEMPTS);

                    String feedback = latestReview.feedback();
                    ComposeStep.ComposeStepResult recomposed = composeStep.execute(
                            analysis, tone, channel, feedback, currentDraft);

                    latestReview = selfReviewStep.review(recomposed.draft(), analysis.evidences(), question);
                    currentDraft = recomposed.draft();
                    finalWarnings = recomposed.formatWarnings();

                    if (latestReview.passed()) {
                        break;
                    }
                }

                finalDraft = currentDraft;
                selfReviewIssues = latestReview.issues();

                if (!latestReview.passed()) {
                    finalWarnings = new ArrayList<>(finalWarnings);
                    finalWarnings.add("SELF_REVIEW_INCOMPLETE");
                    log.warn("self-review did not pass after {} retries inquiryId={}",
                            MAX_RECOMPOSE_ATTEMPTS, inquiryId);
                }
            }
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "COMPLETED", null);
        } catch (Exception e) {
            log.warn("self-review failed, using original draft inquiryId={}", inquiryId, e);
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "FAILED", e.getMessage());
            // Fall through with original draft - self-review is non-blocking
        }

        return new OrchestrationResult(
                analysis, finalDraft, finalWarnings, selfReviewIssues,
                perQuestionEvidences, retrievalQuality, extractedFamilies);
    }

    /** 필터를 적용하여 검색을 수행하고 결과를 반환하는 내부 헬퍼. */
    private RetrieveResult doRetrieveWithFilter(UUID inquiryId, String question,
                                                 List<SubQuestion> subQuestions,
                                                 boolean isMultiQuestion,
                                                 SearchFilter filter) {
        List<EvidenceItem> evidences;
        List<PerQuestionEvidence> perQuestion = null;

        if (isMultiQuestion && retrieveStep instanceof DefaultRetrieveStep defaultStep) {
            perQuestion = executeWithRunLog(inquiryId, "RETRIEVE",
                    () -> defaultStep.executePerQuestion(inquiryId, subQuestions, 10, filter));
            evidences = deduplicateEvidences(perQuestion);
        } else if (retrieveStep instanceof DefaultRetrieveStep defaultStep) {
            evidences = executeWithRunLog(inquiryId, "RETRIEVE",
                    () -> defaultStep.execute(inquiryId, question, 10, filter));
        } else {
            evidences = executeWithRunLog(inquiryId, "RETRIEVE",
                    () -> retrieveStep.execute(inquiryId, question, 10));
        }

        return new RetrieveResult(evidences, perQuestion);
    }

    private record RetrieveResult(List<EvidenceItem> evidences, List<PerQuestionEvidence> perQuestion) {}

    /**
     * 하위 질문별 증거를 flat하게 합치면서 chunkId 기준 중복 제거.
     */
    private List<EvidenceItem> deduplicateEvidences(List<PerQuestionEvidence> perQuestionEvidences) {
        LinkedHashMap<String, EvidenceItem> seen = new LinkedHashMap<>();
        for (PerQuestionEvidence pqe : perQuestionEvidences) {
            for (EvidenceItem ev : pqe.evidences()) {
                seen.putIfAbsent(ev.chunkId(), ev);
            }
        }
        return new ArrayList<>(seen.values());
    }

    private static final int EVIDENCES_PER_SUB_QUESTION = 4;

    /**
     * 하위 질문별 증거 매핑 정보를 additionalInstructions에 구조화하여 추가.
     * 각 하위 질문별로 상위 4개 증거의 excerpt를 직접 포함하여 LLM이 바로 참조 가능하도록 한다.
     */
    private String buildMergedInstructions(String additionalInstructions,
                                           List<PerQuestionEvidence> perQuestionEvidences) {
        if (perQuestionEvidences == null || perQuestionEvidences.isEmpty()) {
            return additionalInstructions;
        }

        StringBuilder sb = new StringBuilder();
        if (additionalInstructions != null && !additionalInstructions.isBlank()) {
            sb.append(additionalInstructions).append("\n\n");
        }

        sb.append("[하위 질문별 증거 매핑]\n");
        for (PerQuestionEvidence pqe : perQuestionEvidences) {
            SubQuestion sq = pqe.subQuestion();
            sb.append("질문 ").append(sq.index()).append(": ").append(sq.question()).append("\n");
            sb.append("증거 충분: ").append(pqe.sufficient() ? "예" : "아니오").append("\n");

            if (pqe.evidences().isEmpty()) {
                sb.append("증거: 없음\n");
            } else {
                int limit = Math.min(EVIDENCES_PER_SUB_QUESTION, pqe.evidences().size());
                for (int i = 0; i < limit; i++) {
                    EvidenceItem ev = pqe.evidences().get(i);
                    sb.append("증거").append(i + 1).append(": ");
                    if (ev.fileName() != null) {
                        sb.append("(").append(ev.fileName());
                        if (ev.pageStart() != null) {
                            sb.append(", p.").append(ev.pageStart());
                            if (ev.pageEnd() != null && !ev.pageEnd().equals(ev.pageStart())) {
                                sb.append("-").append(ev.pageEnd());
                            }
                        }
                        sb.append(", 유사도: ").append(String.format("%.2f", ev.score())).append(") ");
                    }
                    sb.append(ev.excerpt()).append("\n");
                }
            }
            sb.append("---\n");
        }

        return sb.toString();
    }

    private <T> T executeWithRunLog(UUID inquiryId, String step, StepSupplier<T> supplier) {
        long started = System.currentTimeMillis();
        try {
            T result = supplier.get();
            runRepository.save(new OrchestrationRunJpaEntity(
                    UUID.randomUUID(), inquiryId, step, "SUCCESS",
                    System.currentTimeMillis() - started, null, Instant.now()
            ));
            return result;
        } catch (RuntimeException ex) {
            runRepository.save(new OrchestrationRunJpaEntity(
                    UUID.randomUUID(), inquiryId, step, "FAILED",
                    System.currentTimeMillis() - started,
                    ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(),
                    Instant.now()
            ));
            emitPipelineEvent(inquiryId, step, "FAILED", ex.getMessage());
            throw ex;
        }
    }

    private void emitPipelineEvent(UUID inquiryId, String step, String status, String error) {
        try {
            sseService.send(inquiryId, "pipeline-step", Map.of(
                    "step", step,
                    "status", status,
                    "error", error == null ? "" : error
            ));
        } catch (Exception e) {
            log.debug("sse.emit.failed inquiryId={} event=pipeline-step step={}", inquiryId, step);
        }
    }

    @FunctionalInterface
    private interface StepSupplier<T> {
        T get();
    }

    /** 검색 품질 등급 */
    public enum RetrievalQuality {
        /** Level 0: 추출된 제품 필터로 정확히 검색 */
        EXACT,
        /** Level 1: 같은 카테고리 제품으로 확장 검색 */
        CATEGORY_EXPANDED,
        /** Level 2: 필터 없이 전체 검색 (또는 제품 추출 실패) */
        UNFILTERED
    }

    public record OrchestrationResult(
            AnalyzeResponse analysis,
            String draft,
            List<String> formatWarnings,
            List<SelfReviewStep.QualityIssue> selfReviewIssues,
            List<PerQuestionEvidence> perQuestionEvidences,
            RetrievalQuality retrievalQuality,
            Set<String> extractedProductFamilies
    ) {
        /** 하위 호환: selfReviewIssues 없이 생성 */
        public OrchestrationResult(AnalyzeResponse analysis, String draft, List<String> formatWarnings) {
            this(analysis, draft, formatWarnings, List.of(), null, RetrievalQuality.UNFILTERED, Set.of());
        }

        /** 하위 호환: perQuestionEvidences 없이 생성 */
        public OrchestrationResult(AnalyzeResponse analysis, String draft,
                                   List<String> formatWarnings, List<SelfReviewStep.QualityIssue> selfReviewIssues) {
            this(analysis, draft, formatWarnings, selfReviewIssues, null, RetrievalQuality.UNFILTERED, Set.of());
        }

        /** 하위 호환: retrievalQuality/extractedProductFamilies 없이 생성 */
        public OrchestrationResult(AnalyzeResponse analysis, String draft, List<String> formatWarnings,
                                   List<SelfReviewStep.QualityIssue> selfReviewIssues,
                                   List<PerQuestionEvidence> perQuestionEvidences) {
            this(analysis, draft, formatWarnings, selfReviewIssues, perQuestionEvidences,
                    RetrievalQuality.UNFILTERED, Set.of());
        }
    }
}
