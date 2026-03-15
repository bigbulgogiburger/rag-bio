package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.openai.PipelineTraceContext;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaEntity;
import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.infrastructure.rag.budget.TokenBudgetManager;
import com.biorad.csrag.infrastructure.rag.budget.TokenUsage;
import com.biorad.csrag.interfaces.rest.analysis.AnalyzeResponse;
import com.biorad.csrag.interfaces.rest.analysis.EvidenceItem;
import com.biorad.csrag.interfaces.rest.search.AdaptiveRetrievalAgent;
import com.biorad.csrag.interfaces.rest.search.MultiHopRetriever;
import com.biorad.csrag.interfaces.rest.search.ProductExtractorService;
import com.biorad.csrag.interfaces.rest.search.ProductFamilyRegistry;
import com.biorad.csrag.interfaces.rest.search.RerankingService;
import com.biorad.csrag.interfaces.rest.search.SearchFilter;
import com.biorad.csrag.interfaces.rest.sse.SseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class AnswerOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(AnswerOrchestrationService.class);
    private static final int MAX_RECOMPOSE_ATTEMPTS = 2;

    /** 선택 단계별 예상 토큰 사용량 (canProceed 판단용). */
    private static final int ESTIMATED_TOKENS_ADAPTIVE_RETRIEVE = 4000;
    private static final int ESTIMATED_TOKENS_MULTI_HOP = 4000;
    private static final int ESTIMATED_TOKENS_CRITIC = 5000;
    private static final int ESTIMATED_TOKENS_SELF_REVIEW = 4000;

    @Value("${rag.budget.max-tokens-per-inquiry:25000}")
    private int maxBudgetTokens = 25000;

    private final RetrieveStep retrieveStep;
    private final VerifyStep verifyStep;
    private final ComposeStep composeStep;
    private final SelfReviewStep selfReviewStep;
    private final OrchestrationRunJpaRepository runRepository;
    private final SseService sseService;
    private final QuestionDecomposerService questionDecomposerService;
    private final ProductExtractorService productExtractorService;
    private final ProductFamilyRegistry productFamilyRegistry;
    private final AdaptiveRetrievalAgent adaptiveRetrievalAgent;
    private final MultiHopRetriever multiHopRetriever;
    private final CriticAgentService criticAgentService;
    private final PipelineStatusService pipelineStatusService;

    public AnswerOrchestrationService(
            RetrieveStep retrieveStep,
            VerifyStep verifyStep,
            ComposeStep composeStep,
            SelfReviewStep selfReviewStep,
            OrchestrationRunJpaRepository runRepository,
            SseService sseService,
            QuestionDecomposerService questionDecomposerService,
            ProductExtractorService productExtractorService,
            ProductFamilyRegistry productFamilyRegistry,
            AdaptiveRetrievalAgent adaptiveRetrievalAgent,
            MultiHopRetriever multiHopRetriever,
            CriticAgentService criticAgentService,
            PipelineStatusService pipelineStatusService
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
        this.adaptiveRetrievalAgent = adaptiveRetrievalAgent;
        this.multiHopRetriever = multiHopRetriever;
        this.criticAgentService = criticAgentService;
        this.pipelineStatusService = pipelineStatusService;
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel) {
        return run(inquiryId, question, tone, channel, null, null);
    }

    public OrchestrationResult run(UUID inquiryId, String question, String tone, String channel,
                                    String additionalInstructions, String previousAnswerDraft) {

        pipelineStatusService.startExecution(inquiryId);

        try {
            return doRun(inquiryId, question, tone, channel, additionalInstructions, previousAnswerDraft);
        } catch (Exception e) {
            pipelineStatusService.failExecution(inquiryId, e.getMessage());
            throw e;
        }
    }

    private OrchestrationResult doRun(UUID inquiryId, String question, String tone, String channel,
                                       String additionalInstructions, String previousAnswerDraft) {

        TokenBudgetManager budgetManager = new TokenBudgetManager(maxBudgetTokens);

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

        // PipelineTraceContext에서 토큰 사용량 기록 (DECOMPOSE + RETRIEVE)
        recordUsageFromTrace(budgetManager, "DECOMPOSE");
        recordUsageFromTrace(budgetManager, "RETRIEVE");

        // ── ADAPTIVE RETRIEVAL (fallback when 3-Level yields empty) ──
        if (retrievedEvidences.isEmpty() && budgetManager.canProceed("ADAPTIVE_RETRIEVE", ESTIMATED_TOKENS_ADAPTIVE_RETRIEVE)) {
            emitPipelineEvent(inquiryId, "ADAPTIVE_RETRIEVE", "STARTED", null);
            try {
                String productContext = extractedFamilies.isEmpty() ? "" : String.join(", ", extractedFamilies);
                AdaptiveRetrievalAgent.AdaptiveResult adaptiveResult = executeWithRunLog(
                        inquiryId, "ADAPTIVE_RETRIEVE",
                        () -> adaptiveRetrievalAgent.retrieve(question, productContext, inquiryId));

                if (adaptiveResult.status() == AdaptiveRetrievalAgent.AdaptiveResult.ResultStatus.NO_EVIDENCE) {
                    log.info("AdaptiveRetrievalAgent returned NO_EVIDENCE inquiryId={}", inquiryId);
                    emitPipelineEvent(inquiryId, "ADAPTIVE_RETRIEVE", "COMPLETED", "NO_EVIDENCE");
                    // I Don't Know 경로: retrievedEvidences remains empty
                } else {
                    retrievedEvidences = toEvidenceItems(adaptiveResult.evidences());
                    retrievalQuality = RetrievalQuality.ADAPTIVE;
                    log.info("AdaptiveRetrievalAgent found {} evidences, confidence={} inquiryId={}",
                            retrievedEvidences.size(), adaptiveResult.confidence(), inquiryId);
                    emitPipelineEvent(inquiryId, "ADAPTIVE_RETRIEVE", "COMPLETED",
                            "evidences=" + retrievedEvidences.size() + ", confidence=" +
                                    String.format("%.2f", adaptiveResult.confidence()));

                    // per-question 매핑 재구성: Adaptive 증거를 하위 질문별로 재분배
                    if (isMultiQuestion && (perQuestionEvidences == null
                            || perQuestionEvidences.stream().allMatch(pq -> pq.evidences().isEmpty()))) {
                        log.info("Rebuilding per-question evidence mapping from adaptive results inquiryId={}", inquiryId);
                        perQuestionEvidences = rebuildPerQuestionMapping(subQuestions, retrievedEvidences);
                    }
                }
            } catch (Exception e) {
                log.warn("AdaptiveRetrievalAgent failed, continuing with empty results inquiryId={}", inquiryId, e);
                emitPipelineEvent(inquiryId, "ADAPTIVE_RETRIEVE", "FAILED", e.getMessage());
            }
            recordUsageFromTrace(budgetManager, "ADAPTIVE_RETRIEVE");
        } else if (retrievedEvidences.isEmpty()) {
            log.info("pipeline.budget: skipping ADAPTIVE_RETRIEVE (budget insufficient) inquiryId={} remaining={}",
                    inquiryId, budgetManager.getRemainingBudget());
            emitPipelineEvent(inquiryId, "ADAPTIVE_RETRIEVE", "SKIPPED", "budget insufficient");
        }

        emitPipelineEvent(inquiryId, "RETRIEVE", "COMPLETED",
                "retrievalQuality=" + retrievalQuality);

        // ── MULTI_HOP (교차 추론) — 조건부 실행 ────────────────────
        double topScore = retrievedEvidences.isEmpty() ? 0.0
                : retrievedEvidences.stream().mapToDouble(EvidenceItem::score).max().orElse(0.0);
        boolean sufficientEvidence = topScore >= 0.7 && retrievedEvidences.size() >= 3;

        if (!sufficientEvidence && budgetManager.canProceed("MULTI_HOP", ESTIMATED_TOKENS_MULTI_HOP)) {
            try {
                emitPipelineEvent(inquiryId, "MULTI_HOP", "STARTED", null);
                MultiHopRetriever.MultiHopResult multiHopResult = executeWithRunLog(
                        inquiryId, "MULTI_HOP",
                        () -> multiHopRetriever.retrieve(question, inquiryId));

                if (!multiHopResult.isSingleHop()) {
                    List<EvidenceItem> multiHopEvidences = toEvidenceItems(multiHopResult.evidences());
                    if (!multiHopEvidences.isEmpty()) {
                        // Merge multi-hop evidences, deduplicating by chunkId
                        LinkedHashMap<String, EvidenceItem> merged = new LinkedHashMap<>();
                        for (EvidenceItem ev : retrievedEvidences) {
                            merged.putIfAbsent(ev.chunkId(), ev);
                        }
                        for (EvidenceItem ev : multiHopEvidences) {
                            merged.putIfAbsent(ev.chunkId(), ev);
                        }
                        retrievedEvidences = new ArrayList<>(merged.values());
                    }
                    emitPipelineEvent(inquiryId, "MULTI_HOP", "COMPLETED",
                            "hops=" + multiHopResult.hops().size() + ", newEvidences=" + multiHopEvidences.size());
                } else {
                    emitPipelineEvent(inquiryId, "MULTI_HOP", "COMPLETED", "singleHop=true");
                }
            } catch (Exception e) {
                log.warn("MultiHopRetriever failed, continuing with existing evidences inquiryId={}", inquiryId, e);
                emitPipelineEvent(inquiryId, "MULTI_HOP", "FAILED", e.getMessage());
            }
            recordUsageFromTrace(budgetManager, "MULTI_HOP");
        } else if (sufficientEvidence) {
            log.info("pipeline.multihop.skipped: sufficient evidence found topScore={} count={} inquiryId={}",
                    String.format("%.3f", topScore), retrievedEvidences.size(), inquiryId);
            emitPipelineEvent(inquiryId, "MULTI_HOP", "SKIPPED",
                    "sufficient evidence: topScore=" + String.format("%.2f", topScore) + " count=" + retrievedEvidences.size());
        } else {
            log.info("pipeline.budget: skipping MULTI_HOP (budget insufficient) inquiryId={} remaining={}",
                    inquiryId, budgetManager.getRemainingBudget());
            emitPipelineEvent(inquiryId, "MULTI_HOP", "SKIPPED", "budget insufficient");
        }

        // ── VERIFY ─────────────────────────────────────────────────
        final List<EvidenceItem> allEvidences = retrievedEvidences;
        AnalyzeResponse analysis;
        emitPipelineEvent(inquiryId, "VERIFY", "STARTED", null);
        try {
            analysis = executeWithRunLog(inquiryId, "VERIFY",
                    () -> verifyStep.execute(inquiryId, question, allEvidences));
            emitPipelineEvent(inquiryId, "VERIFY", "COMPLETED", null);
        } catch (Exception e) {
            log.warn("Verify step failed, using default analysis inquiryId={}: {}", inquiryId, e.getMessage());
            emitPipelineEvent(inquiryId, "VERIFY", "FAILED", e.getMessage());
            double avgScore = allEvidences.stream().mapToDouble(EvidenceItem::score).average().orElse(0.5);
            String defaultVerdict = avgScore >= 0.7 ? "SUPPORTED" : avgScore >= 0.4 ? "CONDITIONAL" : "REFUTED";
            analysis = new AnalyzeResponse(inquiryId.toString(), defaultVerdict, avgScore,
                    "검증 단계 실패 - 근거 점수 기반 기본 판정", List.of("VERIFY_FAILED"), allEvidences, null);
        }

        final AnalyzeResponse finalAnalysis = analysis;
        recordUsageFromTrace(budgetManager, "VERIFY");

        // ── ROUTING (비용 최적화) ──────────────────────────────
        boolean skipCritic = false;
        boolean skipSelfReview = false;

        double topEvidenceScore = allEvidences.isEmpty() ? 0.0
                : allEvidences.stream().mapToDouble(EvidenceItem::score).max().orElse(0.0);
        double avgEvidenceScore = allEvidences.isEmpty() ? 0.0
                : allEvidences.stream().mapToDouble(EvidenceItem::score).average().orElse(0.0);

        // Critic skip: 다음 조건 중 하나라도 충족하면 Critic을 건너뛴다
        //  a) topScore >= 0.80 AND confidence >= 0.75 (완화된 임계값)
        //  b) 증거 5개 이상 AND 평균 점수 >= 0.75 (풍부한 증거)
        //  c) 하위 질문 1개 (단순 질의)
        //  d) 예산 부족 (budgetManager 판단)
        boolean highConfidence = topEvidenceScore >= 0.80 && finalAnalysis.confidence() >= 0.75;
        boolean abundantEvidence = allEvidences.size() >= 5 && avgEvidenceScore >= 0.75;
        boolean simpleQuery = subQuestions.size() == 1;
        boolean budgetInsufficient = !budgetManager.canProceed("CRITIC", ESTIMATED_TOKENS_CRITIC);

        if (highConfidence || abundantEvidence || simpleQuery || budgetInsufficient) {
            skipCritic = true;
            String skipReason = highConfidence ? "HIGH_CONFIDENCE"
                    : abundantEvidence ? "ABUNDANT_EVIDENCE"
                    : simpleQuery ? "SIMPLE_QUERY"
                    : "BUDGET_INSUFFICIENT";
            log.info("pipeline.routing: skipping CRITIC reason={} inquiryId={} topScore={} confidence={} evidenceCount={} subQuestions={}",
                    skipReason, inquiryId, String.format("%.2f", topEvidenceScore),
                    String.format("%.2f", finalAnalysis.confidence()), allEvidences.size(), subQuestions.size());
            emitPipelineEvent(inquiryId, "ROUTING", "SKIP_CRITIC", "reason=" + skipReason);
        }

        // SelfReview skip: 고신뢰도 경로에서만 건너뛴다
        if (highConfidence) {
            skipSelfReview = true;
            log.info("pipeline.routing: HIGH_CONFIDENCE path, skipping SelfReview inquiryId={} topScore={} confidence={}",
                    inquiryId, topEvidenceScore, analysis.confidence());
            emitPipelineEvent(inquiryId, "ROUTING", "HIGH_CONFIDENCE",
                    "topScore=" + String.format("%.2f", topEvidenceScore));
        } else if (topEvidenceScore < 0.3 && allEvidences.size() <= 2) {
            // Low evidence path: still run full pipeline but flag it
            log.info("pipeline.routing: LOW_EVIDENCE path inquiryId={} topScore={} evidenceCount={}",
                    inquiryId, topEvidenceScore, allEvidences.size());
            emitPipelineEvent(inquiryId, "ROUTING", "LOW_EVIDENCE", null);
        }

        // ── COMPOSE ────────────────────────────────────────────────
        String mergedInstructions = buildMergedInstructions(additionalInstructions, perQuestionEvidences);

        emitPipelineEvent(inquiryId, "COMPOSE", "STARTED", null);

        final AtomicInteger tokenIndex = new AtomicInteger(0);
        ComposeStep.ComposeStepResult composed = executeWithRunLog(inquiryId, "COMPOSE",
                () -> composeStep.executeStreaming(
                        finalAnalysis, tone, channel, mergedInstructions, previousAnswerDraft,
                        chunk -> {
                            sseService.sendChunk(inquiryId, chunk, tokenIndex.getAndIncrement());
                        }
                )
        );

        // 스트리밍 완료 후 전체 답변 확정 이벤트
        sseService.send(inquiryId, "compose-done", Map.of(
                "draft", composed.draft(),
                "tokenCount", tokenIndex.get()
        ));

        emitPipelineEvent(inquiryId, "COMPOSE", "COMPLETED", null);
        recordUsageFromTrace(budgetManager, "COMPOSE");

        // ── CRITIC (팩트 검증) ────────────────────────────────────
        CriticAgentService.CriticResult criticResult = null;
        if (!skipCritic) {
            try {
                final String composedDraft = composed.draft();
                emitPipelineEvent(inquiryId, "CRITIC", "STARTED", null);
                criticResult = executeWithRunLog(inquiryId, "CRITIC",
                        () -> criticAgentService.critique(composedDraft, question, allEvidences));
                emitPipelineEvent(inquiryId, "CRITIC", "COMPLETED",
                        "faithfulness=" + String.format("%.2f", criticResult.faithfulnessScore()));

                if (criticResult.needsRevision()) {
                    log.info("Critic requires revision, recomposing inquiryId={}", inquiryId);
                    String criticFeedback = String.join("\n", criticResult.corrections());
                    emitPipelineEvent(inquiryId, "COMPOSE", "STARTED", "critic 피드백 반영 재작성");

                    tokenIndex.set(0);
                    composed = executeWithRunLog(inquiryId, "COMPOSE",
                            () -> composeStep.executeStreaming(
                                    finalAnalysis, tone, channel, criticFeedback, composedDraft,
                                    chunk -> {
                                        sseService.sendChunk(inquiryId, chunk, tokenIndex.getAndIncrement());
                                    }
                            )
                    );

                    sseService.send(inquiryId, "compose-done", Map.of(
                            "draft", composed.draft(),
                            "tokenCount", tokenIndex.get()
                    ));

                    emitPipelineEvent(inquiryId, "COMPOSE", "COMPLETED", "critic 피드백 반영 완료");
                }
            } catch (Exception e) {
                log.warn("CriticAgent failed, using original draft inquiryId={}", inquiryId, e);
                emitPipelineEvent(inquiryId, "CRITIC", "FAILED", e.getMessage());
            }
            recordUsageFromTrace(budgetManager, "CRITIC");
        } else {
            log.info("pipeline.routing: skipping CRITIC inquiryId={}", inquiryId);
            emitPipelineEvent(inquiryId, "CRITIC", "SKIPPED", "routing decision");
        }

        // ── SELF_REVIEW ────────────────────────────────────────────
        String finalDraft = composed.draft();
        List<String> finalWarnings = composed.formatWarnings();
        List<SelfReviewStep.QualityIssue> selfReviewIssues = List.of();

        // 예산 부족 시 SELF_REVIEW도 건너뛴다
        if (!skipSelfReview && !budgetManager.canProceed("SELF_REVIEW", ESTIMATED_TOKENS_SELF_REVIEW)) {
            skipSelfReview = true;
            log.info("pipeline.budget: skipping SELF_REVIEW (budget insufficient) inquiryId={} remaining={}",
                    inquiryId, budgetManager.getRemainingBudget());
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "SKIPPED", "budget insufficient");
        }

        if (!skipSelfReview) {
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "STARTED", null);
            try {
                final ComposeStep.ComposeStepResult composedForReview = composed;
                SelfReviewStep.SelfReviewResult reviewResult = executeWithRunLog(
                        inquiryId, "SELF_REVIEW",
                        () -> selfReviewStep.review(composedForReview.draft(), finalAnalysis.evidences(), question)
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
            recordUsageFromTrace(budgetManager, "SELF_REVIEW");
        } else {
            log.info("pipeline.routing: skipping SELF_REVIEW inquiryId={}", inquiryId);
            emitPipelineEvent(inquiryId, "SELF_REVIEW", "SKIPPED", "routing decision");
        }

        // ── 예산 요약 로깅 ──────────────────────────────────────
        log.info("pipeline.budget.summary inquiryId={} consumed={} max={} remaining={} overBudget={}",
                inquiryId, budgetManager.getConsumedTokens(), maxBudgetTokens,
                budgetManager.getRemainingBudget(), budgetManager.isOverBudget());

        pipelineStatusService.completeExecution(inquiryId);

        return new OrchestrationResult(
                analysis, finalDraft, finalWarnings, selfReviewIssues,
                perQuestionEvidences, retrievalQuality, extractedFamilies, criticResult);
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

    /** RerankResult → EvidenceItem 변환 헬퍼 (DB 메타데이터 보강: fileName, pageStart, pageEnd, productFamily) */
    private List<EvidenceItem> toEvidenceItems(List<RerankingService.RerankResult> rerankResults) {
        if (retrieveStep instanceof DefaultRetrieveStep defaultStep) {
            return defaultStep.enrichEvidenceMetadata(rerankResults);
        }
        // Fallback: 메타데이터 없이 변환
        return rerankResults.stream()
                .map(r -> new EvidenceItem(
                        r.chunkId().toString(),
                        r.documentId() != null ? r.documentId().toString() : null,
                        r.rerankScore(), r.content(), r.sourceType(),
                        null, null, null, null))
                .toList();
    }

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
     * Adaptive Retrieve 결과를 하위 질문별로 재분배.
     * 각 하위 질문의 키워드가 증거 excerpt에 포함되는지 기반으로 매핑.
     * 매핑되지 않는 증거는 모든 하위 질문에 공통으로 배분.
     */
    private List<PerQuestionEvidence> rebuildPerQuestionMapping(
            List<SubQuestion> subQuestions, List<EvidenceItem> allEvidences) {
        List<PerQuestionEvidence> result = new ArrayList<>();
        for (SubQuestion sq : subQuestions) {
            // 하위 질문 키워드 추출 (2자 이상 토큰)
            Set<String> keywords = java.util.Arrays.stream(
                            sq.question().toLowerCase().split("[\\s,;.?!()]+"))
                    .filter(w -> w.length() >= 2)
                    .collect(java.util.stream.Collectors.toSet());

            List<EvidenceItem> matched = new ArrayList<>();
            for (EvidenceItem ev : allEvidences) {
                String excerptLower = (ev.excerpt() != null ? ev.excerpt() : "").toLowerCase();
                // 증거 excerpt가 하위 질문 키워드 중 2개 이상 포함하면 매핑
                long matchCount = keywords.stream().filter(excerptLower::contains).count();
                if (matchCount >= 2 || ev.score() >= 0.8) {
                    matched.add(ev);
                }
            }

            // 매칭된 증거가 없으면 상위 3개 증거를 공통으로 배정
            if (matched.isEmpty() && !allEvidences.isEmpty()) {
                matched.addAll(allEvidences.subList(0, Math.min(3, allEvidences.size())));
            }

            result.add(PerQuestionEvidence.of(sq, matched));
        }
        return result;
    }

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

    /**
     * PipelineTraceContext에서 해당 단계의 최신 LLM 호출 기록을 읽어 budgetManager에 기록한다.
     * 추적이 시작되지 않은 상태이거나 해당 단계의 기록이 없으면 무시한다.
     */
    private void recordUsageFromTrace(TokenBudgetManager budgetManager, String stepName) {
        PipelineTraceContext.PipelineTrace trace = PipelineTraceContext.current();
        if (trace == null) {
            return;
        }
        int promptSum = 0;
        int completionSum = 0;
        String model = null;
        for (PipelineTraceContext.LlmCallRecord call : trace.getCalls()) {
            if (stepName.equals(call.step())) {
                promptSum += call.inputTokens();
                completionSum += call.outputTokens();
                if (model == null) {
                    model = call.model();
                }
            }
        }
        if (promptSum > 0 || completionSum > 0) {
            budgetManager.recordUsage(TokenUsage.of(stepName, promptSum, completionSum, model));
        }
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
            pipelineStatusService.updateStep(inquiryId, step, status, error);
        } catch (Exception e) {
            log.debug("pipeline.status.update.failed inquiryId={} step={} status={}", inquiryId, step, status);
        }
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
        UNFILTERED,
        /** Level 3: AdaptiveRetrievalAgent로 검색 (3-Level Fallback 실패 시) */
        ADAPTIVE
    }

    public record OrchestrationResult(
            AnalyzeResponse analysis,
            String draft,
            List<String> formatWarnings,
            List<SelfReviewStep.QualityIssue> selfReviewIssues,
            List<PerQuestionEvidence> perQuestionEvidences,
            RetrievalQuality retrievalQuality,
            Set<String> extractedProductFamilies,
            CriticAgentService.CriticResult criticResult
    ) {
        /** 하위 호환: selfReviewIssues 없이 생성 */
        public OrchestrationResult(AnalyzeResponse analysis, String draft, List<String> formatWarnings) {
            this(analysis, draft, formatWarnings, List.of(), null, RetrievalQuality.UNFILTERED, Set.of(), null);
        }

        /** 하위 호환: perQuestionEvidences 없이 생성 */
        public OrchestrationResult(AnalyzeResponse analysis, String draft,
                                   List<String> formatWarnings, List<SelfReviewStep.QualityIssue> selfReviewIssues) {
            this(analysis, draft, formatWarnings, selfReviewIssues, null, RetrievalQuality.UNFILTERED, Set.of(), null);
        }

        /** 하위 호환: retrievalQuality/extractedProductFamilies 없이 생성 */
        public OrchestrationResult(AnalyzeResponse analysis, String draft, List<String> formatWarnings,
                                   List<SelfReviewStep.QualityIssue> selfReviewIssues,
                                   List<PerQuestionEvidence> perQuestionEvidences) {
            this(analysis, draft, formatWarnings, selfReviewIssues, perQuestionEvidences,
                    RetrievalQuality.UNFILTERED, Set.of(), null);
        }

        /** 하위 호환: criticResult 없이 생성 (7-arg) */
        public OrchestrationResult(AnalyzeResponse analysis, String draft, List<String> formatWarnings,
                                   List<SelfReviewStep.QualityIssue> selfReviewIssues,
                                   List<PerQuestionEvidence> perQuestionEvidences,
                                   RetrievalQuality retrievalQuality,
                                   Set<String> extractedProductFamilies) {
            this(analysis, draft, formatWarnings, selfReviewIssues, perQuestionEvidences,
                    retrievalQuality, extractedProductFamilies, null);
        }
    }
}
